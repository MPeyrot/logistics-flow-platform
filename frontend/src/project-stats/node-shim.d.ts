// Types minimaux des modules Node utilisés par project-stats.spec.ts (le projet n'a pas @types/node).
declare module 'node:child_process' {
  export function execFileSync(
    file: string,
    args: string[],
    options?: { cwd?: string; stdio?: string },
  ): unknown;
}
declare module 'node:fs' {
  export function mkdirSync(path: string, options?: { recursive?: boolean }): void;
  export function mkdtempSync(prefix: string): string;
  export function readFileSync(path: string, encoding: 'utf8'): string;
  export function rmSync(path: string, options?: { recursive?: boolean; force?: boolean }): void;
  export function writeFileSync(path: string, data: string): void;
}
declare module 'node:os' {
  export function tmpdir(): string;
}
declare module 'node:path' {
  export function dirname(path: string): string;
  export function join(...parts: string[]): string;
}
