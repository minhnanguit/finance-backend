import { readFile, writeFile } from "node:fs/promises";

const tokens = JSON.parse(
    await readFile(new URL("../tokens/tokens.json", import.meta.url), "utf8")
);
const toCssName = name => name.replace(/[A-Z]/g, letter => `-${letter.toLowerCase()}`);
const renderGroup = group =>
    Object.entries(group)
        .map(([name, value]) => `    --finance-${toCssName(name)}: ${value};`)
        .join("\n");

const css = `/* Generated from tokens/tokens.json. Do not edit by hand. */
:root {
${renderGroup(tokens.light)}
}

@media (prefers-color-scheme: dark) {
    :root {
${renderGroup(tokens.dark).replaceAll("\n", "\n    ")}
    }
}
`;

await writeFile(
    new URL("../src/login/tokens.generated.css", import.meta.url),
    css,
    "utf8"
);
