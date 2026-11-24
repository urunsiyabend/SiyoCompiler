"""Checks for .github/workflows/release.yml.

Run with: /usr/bin/python3 -I .github/tests/test_release_workflow.py
(needs PyYAML and bash).
"""
import os
import subprocess
import sys
import unittest

import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
WORKFLOW = os.path.join(ROOT, ".github", "workflows", "release.yml")

# Standard arm64 macOS labels from docs.github.com "GitHub-hosted runners"
# reference; macos-14 was retired from GitHub Actions on 2026-11-02.
SUPPORTED_MACOS_ARM64 = {"macos-15", "macos-26"}
TAG_EXPR = "${{ env.RELEASE_TAG }}"


def load():
    with open(WORKFLOW, encoding="utf-8") as f:
        # PyYAML (YAML 1.1) reads the bare key `on` as True.
        doc = yaml.safe_load(f)
    doc["on"] = doc.pop(True, doc.get("on"))
    return doc


def step_named(job, name):
    for step in job["steps"]:
        if step.get("name") == name:
            return step
    raise AssertionError(f"no step named {name!r}")


def checkout_steps(job):
    return [s for s in job["steps"] if str(s.get("uses", "")).startswith("actions/checkout@")]


def run_step(script, env):
    full_env = {"PATH": os.environ["PATH"], **env}
    return subprocess.run(["bash", "-e", "-o", "pipefail", "-c", script],
                          env=full_env, capture_output=True, text=True)


class ReleaseWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.wf = load()
        self.jobs = self.wf["jobs"]

    def test_push_tags_trigger_kept(self):
        self.assertEqual(self.wf["on"]["push"], {"tags": ["v*"]})

    def test_dispatch_has_required_release_tag_input(self):
        inp = self.wf["on"]["workflow_dispatch"]["inputs"]["release_tag"]
        self.assertTrue(inp["required"])
        self.assertEqual(inp["type"], "string")

    def test_release_tag_env_selects_input_or_pushed_tag(self):
        self.assertEqual(
            self.wf["env"]["RELEASE_TAG"],
            "${{ github.event_name == 'workflow_dispatch' && inputs.release_tag || github.ref_name }}")

    def test_macos_runner_is_supported_arm64(self):
        mac = [e for e in self.jobs["build"]["strategy"]["matrix"]["include"]
               if e["platform"] == "macos-arm64"]
        self.assertEqual(len(mac), 1)
        self.assertIn(mac[0]["os"], SUPPORTED_MACOS_ARM64)

    def test_matrix_platforms_unchanged(self):
        include = self.jobs["build"]["strategy"]["matrix"]["include"]
        self.assertEqual([(e["platform"], e["archive"]) for e in include],
                         [("linux-x64", "tar.gz"), ("macos-arm64", "tar.gz"),
                          ("windows-x64", "zip")])
        self.assertEqual(include[0]["os"], "ubuntu-latest")
        self.assertEqual(include[2]["os"], "windows-latest")

    def test_every_checkout_uses_selected_tag(self):
        for name, job in self.jobs.items():
            steps = checkout_steps(job)
            self.assertEqual(len(steps), 1, name)
            self.assertEqual(steps[0].get("with", {}).get("ref"), f"refs/tags/{TAG_EXPR}", name)

    def test_tag_validated_before_checkout_and_verified_after(self):
        for name, job in self.jobs.items():
            ids = [s.get("id") or s.get("uses") or s.get("name") for s in job["steps"]]
            co = next(i for i, s in enumerate(job["steps"])
                      if str(s.get("uses", "")).startswith("actions/checkout@"))
            self.assertLess(ids.index("validate_tag"), co, name)
            self.assertGreater(ids.index("verify_tag"), co, name)

    def test_no_input_or_ref_expression_in_run_scripts(self):
        # The tag reaches shell only through the RELEASE_TAG env var.
        for name, job in self.jobs.items():
            for step in job["steps"]:
                run = step.get("run", "")
                for bad in ("inputs.", "github.event.inputs", "github.ref",
                            "env.RELEASE_TAG", "GITHUB_REF_NAME"):
                    self.assertNotIn(bad, run, f"{name}: {step.get('name')}")

    def test_version_extracted_from_validated_env_tag(self):
        for name, job in self.jobs.items():
            run = step_named(job, "Extract version from tag")["run"]
            self.assertIn('${RELEASE_TAG#v}', run, name)
            out = os.path.join(ROOT, "target", f"gh-output-{name}")
            os.makedirs(os.path.dirname(out), exist_ok=True)
            res = run_step(run, {"RELEASE_TAG": "v0.10.0", "GITHUB_OUTPUT": out})
            self.assertEqual(res.returncode, 0, res.stderr)
            with open(out, encoding="utf-8") as f:
                self.assertEqual(f.read(), "VERSION=0.10.0\n")
            os.remove(out)

    def test_validate_step_accepts_only_numeric_semver_tags(self):
        good = ["v0.10.0", "v1.2.3", "v10.0.99"]
        bad = ["", "0.10.0", "v0.10", "v0.10.0-rc1", "v01.2.3", "v1.2.3.4",
               "master", "v1.2.3;id", "v1.2.3\n", "$(id)", "v1.2.3 ", "refs/tags/v1.2.3"]
        for name, job in self.jobs.items():
            run = step_named(job, "Validate release tag")["run"]
            for tag in good:
                res = run_step(run, {"RELEASE_TAG": tag})
                self.assertEqual(res.returncode, 0, f"{name} {tag!r}: {res.stderr}")
            for tag in bad:
                res = run_step(run, {"RELEASE_TAG": tag})
                self.assertNotEqual(res.returncode, 0, f"{name} accepted {tag!r}")

    def test_verify_step_checks_checked_out_commit_is_tag(self):
        for name, job in self.jobs.items():
            run = step_named(job, "Verify checked-out tag")["run"]
            self.assertIn('refs/tags/${RELEASE_TAG}', run, name)
            # Against this repository: an existing tag at HEAD passes only if
            # HEAD is that tag's commit; a missing tag fails.
            head = subprocess.run(["git", "-C", ROOT, "rev-parse", "HEAD"],
                                  capture_output=True, text=True).stdout.strip()
            tags = subprocess.run(["git", "-C", ROOT, "tag", "--points-at", head],
                                  capture_output=True, text=True).stdout.split()
            missing = run_step(f"cd {ROOT!r}\n{run}", {"RELEASE_TAG": "v999.999.999"})
            self.assertNotEqual(missing.returncode, 0, name)
            if tags:
                ok = run_step(f"cd {ROOT!r}\n{run}", {"RELEASE_TAG": tags[0]})
                self.assertEqual(ok.returncode, 0, f"{name}: {ok.stderr}")

    def test_release_action_targets_existing_tag_as_final_release(self):
        step = step_named(self.jobs["release"], "Create GitHub Release")
        self.assertTrue(step["uses"].startswith("softprops/action-gh-release@v2"))
        w = step["with"]
        self.assertEqual(w["tag_name"], TAG_EXPR)
        self.assertIs(w["draft"], False)
        self.assertIs(w["prerelease"], False)
        self.assertNotIn("target_commitish", w)

    def test_action_versions_not_upgraded(self):
        uses = sorted({s["uses"] for j in self.jobs.values() for s in j["steps"] if "uses" in s})
        self.assertEqual(uses, ["actions/checkout@v4", "actions/download-artifact@v4",
                                "actions/setup-java@v4", "actions/upload-artifact@v4",
                                "softprops/action-gh-release@v2"])

    def test_all_run_scripts_parse_as_bash(self):
        for name, job in self.jobs.items():
            for step in job["steps"]:
                if "run" in step:
                    res = subprocess.run(["bash", "-n", "-c", step["run"]],
                                         capture_output=True, text=True)
                    self.assertEqual(res.returncode, 0, f"{name}: {step.get('name')}: {res.stderr}")


if __name__ == "__main__":
    sys.exit(unittest.main())
