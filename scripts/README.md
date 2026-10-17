# scripts

把项目**跑起来 / 看状态 / 关掉**的脚本集合。全部是 bash 脚本。

> **完整用法见项目根目录的 [`USAGE.md`](../USAGE.md)** —— 参数含义、端口表、环境变量、排查清单都在那里。
> 本文件只做索引。

## 脚本一览

| 脚本 | 作用 | 什么时候用 |
| --- | --- | --- |
| `up.sh` | 一键启动(幂等) | 每次开工,或改完代码重启 |
| `health-check.sh` | 容器状态 + 端口/HTTP 健康检查 | 启动后确认、出问题排查 |
| `logs.sh` | 跟随容器日志 | 定位报错 |
| `stop-all.sh` | 停止容器 | 收工、释放内存/端口 |
| `build-all.sh` | 不用 Docker 的纯本地构建(后端 jar + 前端 dist) | 打包发版、验证编译 |
| `start-all.sh` | 只起基础设施 + 打印本地启动提示 | 后端在 IDE 里跑的场景 |

## 在 Windows 上执行

脚本是 bash 写的,Windows 下用 **Git Bash**:

```powershell
& "D:\Git\bin\bash.exe" ./scripts/up.sh
```

两种写法都会失败,别用:

- `scripts/up.sh` —— PowerShell / CMD 不认 `.sh`
- `bash scripts/up.sh` —— PATH 上的 `bash` 是 WSL 的,里面没有 docker

## 看单个脚本的用法

```powershell
& "D:\Git\bin\bash.exe" ./scripts/up.sh -h
& "D:\Git\bin\bash.exe" ./scripts/stop-all.sh -h
```