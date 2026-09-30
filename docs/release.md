# Releasing MachinaWards

The procedure used for v2.4.1 (Modrinth version `UEreZY3w`). Modrinth project: `wards`, id `SX98EKcZ` (owner account Mxchina).

## 1. Version and docs

- `src/main/resources/plugin.yml` `version:` is the version truth.
- `CHANGELOG.md`: a new `## vX.Y.Z` section at the top.
- `MODRINTH.md`: a new `### vX.Y.Z — <title>` entry at the top of the Changelog. This text is also the Modrinth version changelog.
- `WIKI.md`, `TEST_PLAN.md` and the version line in `CLAUDE.md` when behaviour changes.

## 2. Build and boot-verify

```bash
./run.sh                                   # javac --release 21, writes MachinaWards.jar and test-server/plugins/MachinaWards.jar
python3 -c 'import zipfile;print(zipfile.ZipFile("MachinaWards.jar").read("plugin.yml").decode())' | grep version
```

Boot the jar on the floor and the ceiling of the claimed range (Paper 1.21.8 and Paper 26.2, Java 25) and check for a clean
`Enabling MachinaWards vX.Y.Z` with no errors. Paper builds come from the Fill API:
`https://fill.papermc.io/v3/projects/paper/versions/<mc>/builds/latest` → `.downloads."server:default".url`.

## 3. Commit and tag

```bash
git add -A && git commit      # subject: feat(vX.Y.Z): ... or fix(vX.Y.Z): ...; body wrapped at 72; no trailers
git tag vX.Y.Z
```

Pushing to `origin` (GitHub `FranciscoContreras/Machinawards`) is a separate decision; it is not part of a release by default.

## 4. Publish on Modrinth (API v2)

The token is read from `MODRINTH_TOKEN` and only ever sent as the `Authorization` header; never print it.
Send a `User-Agent` that names the project, e.g. `FranciscoContreras/MachinaWards-release`.

```bash
UA='FranciscoContreras/MachinaWards-release'
API=https://api.modrinth.com/v2
# copy loaders and game_versions from the newest version
curl -fsS -A "$UA" -H "Authorization: $MODRINTH_TOKEN" $API/project/SX98EKcZ/version | jq -c '.[0] | {version_number,loaders,game_versions}'
```

`data.json` (the version metadata):

```json
{
  "project_id": "SX98EKcZ",
  "name": "MachinaWards X.Y.Z — <title>",
  "version_number": "X.Y.Z",
  "changelog": "<the MODRINTH.md entry, bullets only>",
  "dependencies": [],
  "game_versions": ["1.21", "...", "26.2"],
  "version_type": "release",
  "loaders": ["paper", "purpur"],
  "featured": false,
  "status": "listed",
  "file_parts": ["jar"],
  "primary_file": "jar"
}
```

```bash
cp MachinaWards.jar /tmp/MachinaWards-X.Y.Z.jar
curl -fsS -A "$UA" -H "Authorization: $MODRINTH_TOKEN" \
  -F "data=@data.json;type=application/json" \
  -F "jar=@/tmp/MachinaWards-X.Y.Z.jar;type=application/java-archive" \
  $API/version | jq '{id,version_number}'
# read back and compare the hash with the local jar
curl -fsS -A "$UA" $API/version/<id> | jq '{id,version_number,game_versions,loaders,sha512:.files[0].hashes.sha512}'
shasum -a 512 MachinaWards.jar
```

Versions before 2.4.1 were marked `featured: true` on the listing; set it in the Modrinth UI if the new one should be featured too.
