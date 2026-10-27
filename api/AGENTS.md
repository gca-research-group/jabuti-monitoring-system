# Agent instructions

## Commit messages

Use semantic commit messages (Conventional Commits) for every Git commit:

```text
<type>[optional scope][!]: <description>
```

- Choose the type that describes the change: `feat`, `fix`, `docs`, `refactor`, `test`, `perf`, `build`, `ci`, `style`, or `chore`.
- Write a concise description in the imperative mood, such as `fix(auth): reject expired tokens`.
- Mark breaking changes with `!` before the colon and explain them in a `BREAKING CHANGE:` footer.
- Keep each commit focused on one logical change.
