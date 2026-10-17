# 操作手册

本项目**启动 / 查看 / 关闭**的全部命令。所有命令都在项目根目录执行:

```powershell
cd D:\JAVA\SpringAIStudent\java-agent-platform
```

- 只想快速上手 → 看下面的 **速查表**
- 想搞清楚参数含义、排查报错 → 往下看对应章节
- 技术架构、接口清单、实现细节 → 见 `README.md`

---

## 速查表

| 我想…… | 命令(PowerShell) |
| --- | --- |
| 全栈启动 | `& "D:\Git\bin\bash.exe" ./scripts/up.sh` |
| 快速重启(不重建镜像) | `& "D:\Git\bin\bash.exe" ./scripts/up.sh --no-build` |
| 只起基础设施,后端在 IDE 里跑 | `& "D:\Git\bin\bash.exe" ./scripts/up.sh --infra-only` |
| 连监控一起起 | `& "D:\Git\bin\bash.exe" ./scripts/up.sh --with-monitoring` |
| 看运行状态 | `& "D:\Git\bin\bash.exe" ./scripts/health-check.sh` |
| 看某个服务日志 | `& "D:\Git\bin\bash.exe" ./scripts/logs.sh gateway-service` |
| 看全部日志 | `& "D:\Git\bin\bash.exe" ./scripts/logs.sh` |
| 全部关掉 | `& "D:\Git\bin\bash.exe" ./scripts/stop-all.sh` |
| 关掉并清空数据 | `& "D:\Git\bin\bash.exe" ./scripts/stop-all.sh -v` |
| 不用 Docker 纯本地构建 | `& "D:\Git\bin\bash.exe" ./scripts/build-all.sh` |

在 Git Bash 窗口里,把前面的 `& "D:\Git\bin\bash.exe" ` 去掉,直接敲 `./scripts/up.sh` 即可。

---

## 0. 在 Windows 上怎么执行脚本

Windows 上有三个叫 "bash" 的东西,选错就会报看不懂的错:

| 你敲的命令 | 结果 | 原因 |
| --- | --- | --- |
| `scripts/up.sh` | ❌ `CommandNotFoundException` | PowerShell / CMD 不认 `.sh`,只会把它当成命令名去找 |
| `bash scripts/up.sh` | ❌ `未找到 docker compose` | PATH 上的 `bash` 是 `C:\Windows\System32\bash.exe`,即 **WSL**;WSL 是另一个系统,默认看不到 Windows 的 docker |
| `& "D:\Git\bin\bash.exe" ./scripts/up.sh` | ✅ | 用 **Git Bash**,它继承了 Windows PATH,能直接调 `docker.exe` / `docker compose` |
| 打开 Git Bash 窗口,再 `./scripts/up.sh` | ✅ | 同上 |

想看某个脚本的用法,加 `-h`:

```powershell
& "D:\Git\bin\bash.exe" ./scripts/up.sh -h
& "D:\Git\bin\bash.exe" ./scripts/stop-all.sh -h
```

---

## 1. 启动:`up.sh`

一键启动,按顺序做 5 件事:

1. 检查 docker / docker compose;**Docker Desktop 没运行就自动拉起**,最多等 300 秒
2. 准备 `.env`(没有就从 `.env.example` 复制)
3. 构建前端产物 `frontend/dist`(Nginx 直接挂载这个目录)
4. `docker compose up -d`(必要时加 `--profile app` / `--build`)
5. 等 MySQL、Redis、Nacos、5 个后端、Nginx 就绪,最后打印访问入口

### 参数

| 参数 | 作用 |
| --- | --- |
| *(无)* | 全栈:基础设施 + 5 个后端 + 前端,并重建后端镜像 |
| `--no-build` | 跳过前端构建与镜像重建,只快速重启已有容器(最快,约 50 秒) |
| `--infra-only` | 只起 mysql / redis / nacos / nginx |
| `--with-monitoring` | 额外启动 Prometheus + Grafana(**不能和 `--infra-only` 同时用**) |
| `--logs` | 启动完成后跟随日志(Ctrl+C 只退出跟随,不停容器) |
| `-h` / `--help` | 打印用法 |

### 可以反复执行

`docker compose up -d` 天然幂等,所以不用先关再开:

- 容器**不存在** → 创建
- 容器**已停止** → 启动
- 配置或镜像定义**变了** → 重建

### 示例

```powershell
& "D:\Git\bin\bash.exe" ./scripts/up.sh                      # 全栈（首次约 2~3 分钟）
& "D:\Git\bin\bash.exe" ./scripts/up.sh --no-build           # 快速重启
& "D:\Git\bin\bash.exe" ./scripts/up.sh --infra-only         # 只起基础设施
& "D:\Git\bin\bash.exe" ./scripts/up.sh --with-monitoring    # 带监控
& "D:\Git\bin\bash.exe" ./scripts/up.sh --no-build --logs    # 起完直接看日志
```

### 注意

- 首次全栈构建约 **2~3 分钟**。5 个镜像并行构建,Maven 依赖走阿里云镜像 + BuildKit 共享缓存。
- `--infra-only` 只是"不带 `--profile app"`,**不会**停掉已经在跑的后端容器,但也不会去拉起它们。

---

## 2. 查看:`health-check.sh`

输出四段:容器状态表 → 基础设施 → 后端服务 → 监控状态。端口**全部从 `.env` 读**,改端口不用改脚本。

| 参数 | 作用 |
| --- | --- |
| *(无)* | 完整输出 |
| `--quiet` | 只输出异常项;有异常时退出码为 1(适合 CI) |

判读规则:

- `[OK]` 通过;`[FAIL]` 异常;`[--]` 该项未启动(不算异常)
- `Nginx->网关->用户` 返回 **401 也算通过** —— 说明请求已经穿过 Nginx、网关、路由到达 user-service,只是鉴权拦下了,链路是通的
- 监控没启动时显示 `[--] 未启动（./scripts/up.sh --with-monitoring 可开启）`

```powershell
& "D:\Git\bin\bash.exe" ./scripts/health-check.sh
& "D:\Git\bin\bash.exe" ./scripts/health-check.sh --quiet
```

---

## 3. 看日志:`logs.sh`

| 用法 | 作用 |
| --- | --- |
| `./scripts/logs.sh` | 跟随**全部**服务日志(最近 100 行) |
| `./scripts/logs.sh gateway-service` | 只看网关 |
| `./scripts/logs.sh ai-service --tail=500` | 自定义行数 |

服务名用 **compose 服务名**(不是容器名 `jap-xxx`):

```
mysql  redis  nacos  nginx
gateway-service  user-service  ai-service  search-service  project-service
prometheus  grafana
```

Ctrl+C 退出跟随,**不会**停容器。

```powershell
& "D:\Git\bin\bash.exe" ./scripts/logs.sh
& "D:\Git\bin\bash.exe" ./scripts/logs.sh user-service
& "D:\Git\bin\bash.exe" ./scripts/logs.sh mysql --tail=200
```

---

## 4. 关闭:`stop-all.sh`

| 参数 | 作用 |
| --- | --- |
| *(无)* | 停全部容器(基础设施 + 5 个后端 + 监控),**保留数据卷** |
| `-v` / `--volumes` | 连数据卷一起删:MySQL / Redis / Nacos 数据全部清空,下次启动重新执行 `infrastructure/mysql/init` 下的初始化 SQL |
| `--infra` | 只停基础设施(后端在 IDE 里跑的场景) |
| `-h` / `--help` | 打印用法 |

实际执行的是:

```bash
docker compose --profile app --profile monitoring down --remove-orphans
```

**为什么必须带 profile**:`docker compose down` 只处理"当前 profile 选中"的服务。不带 `--profile app` 时,5 个后端容器不在项目模型里,会被漏掉、继续跑。

```powershell
& "D:\Git\bin\bash.exe" ./scripts/stop-all.sh
& "D:\Git\bin\bash.exe" ./scripts/stop-all.sh --infra
& "D:\Git\bin\bash.exe" ./scripts/stop-all.sh -v
```

---

## 5. 另外两个脚本

### `build-all.sh` —— 不用 Docker 的纯本地构建

无参数。依次执行 `mvn -DskipTests clean package` 和 `cd frontend && npm run build`。
产物:`backend/**/target/*.jar`、`frontend/dist`。

```powershell
& "D:\Git\bin\bash.exe" ./scripts/build-all.sh
```

### `start-all.sh` —— 基础设施 + 本地开发提示

无参数。相当于 `up.sh --infra-only` 的轻量版:起 mysql / redis / nacos / nginx,再打印后端和前端在本机启动的命令。
**不构建前端**,也不检查 Docker 是否在运行。

```powershell
& "D:\Git\bin\bash.exe" ./scripts/start-all.sh
```

---

## 6. 三个 compose profile

哪些容器会被启动,由 profile 决定:

| profile | 包含的服务 | 谁负责拉起 |
| --- | --- | --- |
| *(默认)* | mysql / redis / nacos / nginx | `up.sh`、`start-all.sh` |
| `app` | gateway / user / ai / search / project-service | `up.sh`(默认带) |
| `monitoring` | prometheus / grafana | `up.sh --with-monitoring` |

---

## 7. 端口与访问入口

| 服务 | 地址 | 账号 |
| --- | --- | --- |
| 前端(Nginx) | http://localhost:80 | — |
| 网关 API | http://localhost:18080/api | 需 `Authorization: Bearer <token>` |
| user-service | http://localhost:8081 | — |
| ai-service | http://localhost:8082 | — |
| search-service | http://localhost:8083 | — |
| project-service | http://localhost:8084 | — |
| Nacos 控制台 | http://localhost:8848/nacos | 免登录(开发配置) |
| MySQL | 127.0.0.1:3307 | root / root |
| Redis | 127.0.0.1:6380 | 密码 `redis123` |
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | admin / admin |

对外只暴露 `/api/**`,网关按前缀路由并去掉 `/api`:

| 外部路径 | 目标服务 |
| --- | --- |
| `/api/user/**` | user-service |
| `/api/ai/**` | ai-service |
| `/api/search/**` | search-service |
| `/api/project/**` | project-service |

除 `/api/user/register`、`/api/user/login` 外,其余 `/api/**` 都需要 JWT。

### 前端本地开发(不经 Nginx)

```powershell
cd frontend
$env:VITE_GATEWAY_TARGET = "http://127.0.0.1:18080"
npm run dev
```

vite 的 `/api` 代理默认指向 `8080`,本项目网关在 `18080`,所以必须设置 `VITE_GATEWAY_TARGET`。

---

## 8. 环境变量:`.env` 和 `.env.local` 别搞混

| 文件 | 谁读它 | 用途 |
| --- | --- | --- |
| `.env` | **docker compose** | 宿主机端口映射、注入容器内后端的密钥 |
| `.env.local` | **各后端服务**(`application.yml` 里的 `spring.config.import`) | 后端在**本机**直接跑时的连接信息 |

⚠️ 两个文件的 MySQL 配置**指向不同的库**:

| | `.env` | `.env.local` |
| --- | --- | --- |
| `MYSQL_PORT` | `3307`(容器 MySQL) | `3306`(本机安装的 MySQL) |
| `MYSQL_PASSWORD` | `root` | `20050608` |

结论:**容器里跑后端 → 看 `.env`;IDE 里跑后端 → 看 `.env.local`**。两边数据不互通,别在一边建了表另一边找不到。

两个文件都已在 `.gitignore` 中,密钥不会进仓库。

### 关键变量

| 变量 | 当前值 | 说明 |
| --- | --- | --- |
| `MYSQL_PORT` | `3307` | 宿主机 3306 常被本机 MySQL 占用,故容器映射到 3307 |
| `REDIS_PORT` | `6380` | 同理,避开本机 6379 |
| `NACOS_HTTP_PORT` | `8848` | 控制台 `/nacos` |
| `NACOS_GRPC_PORT` | `9848` | 客户端 gRPC,**必须一起映射**,否则服务注册不上 |
| `NGINX_HTTP_PORT` | `80` | 前端入口 |
| `GATEWAY_HTTP_PORT` | `18080` | 网关在宿主机的端口。容器内部仍走 `gateway-service:8080`。本机 8080 被其它项目占用,故改 18080 |
| `DEEPSEEK_API_KEY` | 已配置 | `ai-service` 对话 / Agent 必需 |
| `GITEE_ACCESS_TOKEN` | 已配置 | `search-service` 关键词搜索必需 |
| `JWT_SECRET` | 已配置 | gateway 与 user-service **必须完全一致**,否则令牌无法互认 |

---

## 9. 排查清单

| 现象 | 原因 / 处理 |
| --- | --- |
| `未找到 docker compose（需要 Docker Compose v2 插件）` | 你在 WSL 的 bash 里跑脚本。改用 Git Bash |
| `scripts/up.sh : 无法将...识别为 cmdlet` | PowerShell 不认 `.sh`。用 `& "D:\Git\bin\bash.exe" ./scripts/up.sh` |
| `Child module ... does not exist` | 镜像构建时 Dockerfile 只拷了单个模块的 pom。应为整仓拷贝(当前已是正确写法) |
| `docker compose` 报端口已被占用 | 改 `.env` 里的端口后重跑 `up.sh --no-build` |
| 改了 Java 代码,容器没变化 | `up.sh` 默认带 `--build`;用了 `--no-build` 就不会重建镜像 |
| 前端打开是 404 | `frontend/dist` 不存在。重跑 `up.sh`(不带 `--no-build`) |
| Nacos 里看不到服务 | 检查 `NACOS_GRPC_PORT=9848` 是否映射、服务能否连上 `nacos:8848` |
| 接口 404 但服务是好的 | 网关路由带 `/api` 前缀,例如 `/api/project`,别漏 |
| 接口 401 `未登录，请先登录` | 该路径需要 JWT。先调 `/api/user/login` 拿 token |
| Grafana / Prometheus 没跑 | 属 `monitoring` profile,默认不启动。用 `up.sh --with-monitoring` |
| Grafana 里没数据 | `prometheus.yml` 抓的是容器内地址(`gateway-service:8080` 等),后端必须跑在容器里 |
| Gitee 搜索返回空数组 | Gitee 官方 `/v5/search/repositories` 目前恒返回空(服务端问题)。代码会降级为"在授权账号自己的仓库中筛选",换个自己仓库里有的关键词即可 |

---

## 10. 典型场景

**第一次把项目跑起来**

```powershell
& "D:\Git\bin\bash.exe" ./scripts/up.sh
& "D:\Git\bin\bash.exe" ./scripts/health-check.sh
```

**日常开发(后端在 IDE 里跑)**

```powershell
& "D:\Git\bin\bash.exe" ./scripts/up.sh --infra-only
# 然后在 IDEA 里启动各 Application，或：
# mvn -pl backend/gateway-service spring-boot:run
cd frontend; npm run dev
```

**改完 Java 代码重启(容器方式)**

```powershell
& "D:\Git\bin\bash.exe" ./scripts/up.sh --no-build
```

**面试 / 演示**

```powershell
& "D:\Git\bin\bash.exe" ./scripts/up.sh --with-monitoring
# 浏览器打开 http://localhost ，Grafana 在 http://localhost:3000
```

**收工**

```powershell
& "D:\Git\bin\bash.exe" ./scripts/stop-all.sh
```

**CI 里做健康检查**(Linux runner 上直接跑,有异常退出码为 1)

```bash
./scripts/health-check.sh --quiet
```