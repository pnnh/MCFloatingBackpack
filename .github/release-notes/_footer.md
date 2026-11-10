## 安装与使用 / Installation

| File | Environment | Description |
| ---- | ----------- | ----------- |
| `mcfloatingbackpack-{{version}}.jar` | Minecraft 1.21.1 / NeoForge 21.1.251+ | 双端必需 / Required on both client and server |

请将本模组同时放置在**服务器**和**客户端**的 `mods/` 文件夹中，并保持**两端版本一致**（MAJOR.MINOR 相同；登录时会自动检查并提示）。升级或降级前请先备份存档。
Install this mod in the `mods/` directory of **both** your server and client, and keep **both on the same version** (same MAJOR.MINOR; it is checked and reported at login). Back up your world before updating or downgrading.

想检查客户端与服务器的模组版本是否一致，可以另外安装 [MCompatIssues](https://github.com/pnnh/MCompatIssues)（可选）。
To check whether the mod versions on your client and server match, you can install [MCompatIssues](https://github.com/pnnh/MCompatIssues) (optional).

## 构建溯源 / Build provenance

所有 Release 附件均包含官方 GitHub Artifact Attestation（基于 Sigstore OIDC 的可信来源证明）。
可在终端中使用官方 GitHub CLI 验证构建产物的真实性与未经篡改：
Every release asset ships with a GitHub Artifact Attestation, verifiable with the GitHub CLI:

```bash
gh attestation verify mcfloatingbackpack-{{version}}.jar --repo {{repository}}
```
