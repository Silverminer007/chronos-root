// opencode-Plugin: bringt Wiki-Regeln und -Index in jeden Systemprompt und schützt docs/.
// Gegenstück zu den Claude-Code-Hooks in .claude/settings.json (gleiche Skripte unter scripts/).
import { execFileSync } from "node:child_process"
import path from "node:path"

export const ChronosWiki = async ({ directory }) => {
  const script = path.join(directory, "scripts", "wiki-context.sh")
  const docsDir = path.join(directory, "docs") + path.sep

  return {
    "experimental.chat.system.transform": async (_input, output) => {
      try {
        output.system.push(execFileSync(script, { encoding: "utf8" }))
      } catch {
        // Wiki-Kontext ist ein Zusatz; ohne ihn läuft die Session normal weiter.
      }
    },
    "tool.execute.before": async (input, output) => {
      if (!["write", "edit", "patch", "multiedit"].includes(input.tool)) return
      const target = output.args?.filePath ?? output.args?.file_path ?? ""
      if (path.resolve(directory, target).startsWith(docsDir)) {
        throw new Error(
          "docs/ ist freigabepflichtig (docs/README.md). Nicht ändern; Vorschlag nach wiki/proposals/ schreiben " +
            "oder den Menschen um ausdrückliche Freigabe bitten.",
        )
      }
    },
  }
}
