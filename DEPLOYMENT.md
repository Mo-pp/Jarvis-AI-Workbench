# Jarvis 服务器 Docker 部署

`docs/references/demo` 的部署方式是“打 Spring Boot jar，再用 Dockerfile 跑 jar”。Jarvis 可以沿用这个思路，但它不是单体后端：还需要前端静态站点、MySQL、Redis、RabbitMQ，以及运行时 API Key。推荐直接用本仓库的 `docker-compose.yml` 一次拉起整套服务。

## 服务器要求

- Linux 云服务器，建议 Ubuntu 22.04/24.04。
- Docker Engine 和 Docker Compose Plugin。
- 建议至少 2 核 4GB；如果频繁构建镜像或用 PDF 导出，4 核 8GB 更稳。
- 服务器安全组只需要开放 Web 端口，默认 `80`。MySQL、Redis、RabbitMQ 不对公网开放。

## 1. 上传代码

推荐把项目推到 Git 仓库后在服务器拉取：

```bash
git clone <your-repo-url> /opt/jarvis
cd /opt/jarvis
```

如果暂时不用 Git，也可以用 `scp` 或面板上传项目目录，但不要上传本地 `.env`、`application-dev.yml`、`application-local.yml`。

## 2. 准备环境变量

```bash
cp .env.deploy.example .env
nano .env
```

必须修改：

- `MYSQL_ROOT_PASSWORD`
- `RABBITMQ_DEFAULT_PASS`
- `JARVIS_SECURITY_KEY`
- 至少一个 LLM Key，例如 `OPENAI_API_KEY`，并确认 `JARVIS_LLM_PROVIDER`
- `OPENVIKING_BASE_URL` 和 `OPENVIKING_API_KEY`，如果你要用知识库、Skill、长期记忆
- `MAIL_USERNAME` 和 `MAIL_PASSWORD`，如果你要开放邮箱验证码注册

生成 JWT 密钥示例：

```bash
openssl rand -hex 32
```

## 3. 构建并启动

```bash
docker compose build
docker compose up -d
```

查看状态：

```bash
docker compose ps
docker compose logs -f jarvis-api
```

第一次启动时，后端会通过 Flyway 自动创建/迁移 MySQL 表结构。启动完成后访问：

```text
http://<服务器公网IP>
```

## 4. 域名和 HTTPS

如果你有域名，把 A 记录解析到服务器公网 IP。HTTPS 推荐放在宿主机 Nginx、Caddy 或云厂商负载均衡上，再反代到 `http://127.0.0.1:<JARVIS_WEB_PORT>`。

如果服务器已经有 Nginx 占用 80 端口，把 `.env` 里的 `JARVIS_WEB_PORT` 改成例如 `18080`：

```env
JARVIS_WEB_PORT=18080
```

然后宿主机 Nginx 反代到：

```text
http://127.0.0.1:18080
```

## 5. 更新部署

```bash
cd /opt/jarvis
git pull
docker compose build
docker compose up -d
docker compose logs -f jarvis-api
```

## 常见问题

- `jarvis-api` 启动失败：先看 `docker compose logs -f jarvis-api`，重点检查 MySQL 密码、LLM Key、OpenViking 地址。
- 页面能打开但接口 502：后端还没启动完成或启动失败，继续看 `jarvis-api` 日志。
- SSE 流式响应中断：确认没有额外 Nginx 开启响应缓冲；本仓库容器内 Nginx 已对 `/api/` 关闭 `proxy_buffering`。
- PDF 导出失败：后端镜像使用 Playwright Java 运行层，正常包含 Chromium 依赖；如果你改了后端基础镜像，需要重新安装 Playwright 浏览器依赖。
- 注册收不到邮件：确认 `MAIL_USERNAME` 是完整邮箱，`MAIL_PASSWORD` 是 SMTP 授权码，不是登录密码。

## 安全注意

不要把本地 `src/main/resources/application-dev.yml` 或 `application-local.yml` 放到服务器镜像或 Git 仓库里。线上密钥只写服务器 `.env`，并定期轮换。
