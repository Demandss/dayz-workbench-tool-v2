# Security policy

## Reporting a problem

Please do not report a security problem in a public issue. Use **Security → Report a vulnerability** on the GitHub page of this repository, which sends it to the maintainers in private.

Describe what you found, how to reproduce it and what it lets someone do. You will get an answer as soon as the maintainers can give one; this is a hobby project, so there is no fixed time.

## What counts

The program starts processes, makes symbolic links in the game folder and, when switched on, runs a local MCP server in the layout editor. These are the parts where a problem matters most:

- the MCP server (it should listen on `127.0.0.1` only and accept only requests with the right bearer token);
- reading and writing files outside the folders the user chose;
- running of commands built from settings or file names.

## Supported versions

Only the latest release gets fixes.
