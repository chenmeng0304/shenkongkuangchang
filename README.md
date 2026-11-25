# 深空矿场 · 服务端（微信云托管版）

《深空矿场》小游戏的联机后端：账号 / 存档同步 / 世界 Boss（含基地防线）/ 竞技场 / 排行榜。
Spring Boot 3.3 + Java 17 + MySQL 8 + Flyway，**已按微信云托管的容器规范改好**。aaa

> 本目录是云托管专用工程；原来跑在自建服务器上的版本在 `D:\wxdeepmine-backend`（带 docker-compose 与部署脚本）。

## 目录结构

```
skkc/
├── Dockerfile               多阶段构建：Maven 编译 → JRE 17 运行（EXPOSE 80）
├── .dockerignore
├── container.config.json    模板部署用的初始「服务设置」（端口 / 规格 / 环境变量）
├── settings.xml             Maven 国内镜像（加速构建）
├── pom.xml                  Spring Boot 3.3.5 / Java 17
├── mvnw / mvnw.cmd
└── src/main/
    ├── java/com/deepmine/   auth / boss / pvp / profile / leaderboard / config …
    └── resources/
        ├── application.yml  端口、数据源（自动读云托管注入的 MYSQL_*）
        └── db/migration/    Flyway 建表脚本 V1~V4（首次启动自动建表）
```

## 一、部署到微信云托管

前置：在 [微信云托管控制台](https://cloud.weixin.qq.com/cloudrun) 创建环境，并创建一个 MySQL 实例。

1. **把本目录推到 Git 仓库**（当前 remote：`https://github.com/chenmeng0304/shenkongkuangchang.git`）

   ```bash
   cd D:\skkc
   git add -A
   git commit -m "深空矿场服务端：云托管版"
   git push origin main
   ```

2. **新建服务**：控制台 → 服务管理 → 新建服务
   - 服务名称：`deepmine`
   - 部署方式：**Git 仓库拉取**（选择上面的仓库与分支）
   - 构建目录：`/`（Dockerfile 在根目录）
   - **端口：80**（必须与 Dockerfile 的 `EXPOSE 80` 一致）

3. **绑定 MySQL**：服务设置 → 数据库 → 绑定云托管 MySQL
   - 绑定后平台会自动注入 `MYSQL_ADDRESS` / `MYSQL_USERNAME` / `MYSQL_PASSWORD`

4. **建库**：在云托管 MySQL 的「数据库管理」里执行一次

   ```sql
   CREATE DATABASE IF NOT EXISTS deepmine
     DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

   （表结构不用手建，服务启动时 Flyway 会自动跑 `db/migration/V1~V4`）

5. **环境变量**（服务设置 → 环境变量）

   | 变量 | 必填 | 说明 |
   | --- | --- | --- |
   | `PORT` | 是 | 固定 `80` |
   | `DB_NAME` | 是 | `deepmine`（数据库名） |
   | `APP_JWT_SECRET` | **是** | 换成你自己的长随机串，别用默认值 |
   | `TZ` | 否 | `Asia/Shanghai` |
   | `BOSS_PERIOD` | 否 | 每期 Boss 秒数，默认 `86400`（每天一轮） |
   | `PVP_COOLDOWN` | 否 | 竞技场冷却秒数，默认 `10` |
   | `MYSQL_ADDRESS` 等 | 自动 | 绑定 MySQL 后由平台注入，**不要手填** |

6. **发布**：控制台 → 版本管理 → 部署 → 选择分支 → 构建并发布
   构建日志里会看到 Maven 打包与 `java -jar /app/app.jar` 启动。

7. **验证**：打开 `https://<你的服务域名>/api/health`，应返回

   ```json
   {"service":"deepmine-server","ok":true,"time":"..."}
   ```

## 二、客户端对接

小程序 `D:\wxdeepmine\config.js`：

```js
export const DEFAULT_API_BASE = 'https://<云托管服务域名>';  // 不要带 /api
```

客户端会自动拼 `/api`，改完重新编译小游戏即可。
云托管默认给的是 **HTTPS 域名**，正式版小游戏还需要在小程序后台
「开发设置 → 服务器域名」把该域名加进 `request 合法域名`。

## 二、客户端怎么调（wx.cloud.callContainer）

小游戏端**优先走云托管**，不需要域名白名单、不需要备案：

```js
wx.cloud.init({ env: 'prod-d9g6iiulv5b41d013', traceUser: false });

wx.cloud.callContainer({
  config: { env: 'prod-d9g6iiulv5b41d013' },
  path: '/api/health',                    // 后端路径，必须带 /api 前缀
  header: { 'X-WX-SERVICE': 'springboot-ou70' },
  method: 'GET',
});
```

- `env`：云开发环境 ID（控制台 → 云开发 → 设置）
- `X-WX-SERVICE`：**云托管服务名称**（控制台 → 服务列表里那串英文名，这里假设是 `springboot-ou70`）
- 云托管服务可以**关闭公网访问**，`callContainer` 照样能调通（走内网），更安全
- 客户端代码里这两个值在 `D:\wxdeepmine\config.js` 的 `CONFIG.CLOUD`，改完重新编译小游戏即可
- 云调用失败会自动降级到 `wx.request` 的 http 备用通道

## 三、注意事项

- **实例数必须 ≥ 1**：世界 Boss 靠服务内的 `@Scheduled` 每天轮换，实例缩容到 0 就停摆（`minNum: 1`）。
- **先别开多实例**：定时任务在每个实例都会跑；虽然轮换逻辑用数据库行锁兜底，但 1 个实例最省心（`maxNum: 1`）。要扩容时告诉我，我把定时任务改成分布式锁。
- **JWT 密钥**：`APP_JWT_SECRET` 一定要改，否则任何人都能伪造 token。
- **时区**：容器时区已固定为 `Asia/Shanghai`，Boss 的「每天 0 点」按北京时间算。
- **数据备份**：云托管 MySQL 控制台可以手动备份/回滚。

## 四、本地开发

```powershell
# 需要本地 MySQL（默认 127.0.0.1:3306/deepmine）
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/deepmine?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
$env:DB_USER="deepmine"
$env:DB_PASSWORD="deepmine123"
.\mvnw.cmd spring-boot:run
```

跑在 `http://127.0.0.1:8081`（本地默认端口），客户端的 `LOCAL_API_BASE` 已指向这里。

## 五、接口一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 健康检查 |
| POST | `/api/auth/register` `/api/auth/login` | 注册 / 登录（JWT） |
| GET | `/api/me` · `/api/me/snapshot` | 档案 / 下载云端存档 |
| POST | `/api/me/sync` | 上传存档（含装备 / 防线 / 战力） |
| GET | `/api/boss` · POST `/api/boss/attack` · `/api/boss/claim` | 世界 Boss |
| GET | `/api/pvp/players` · `/api/pvp/players/{id}` | 竞技场列表 / 查看对手装备 |
| POST | `/api/pvp/battle` · GET `/api/pvp/history` | 对战 / 战绩 |
| GET | `/api/public/leaderboard?type=power\|facilities\|rating\|boss` | 排行榜 |

## 六、微信登录

客户端不再用账号密码，改成 **wx.login() → 后端换 openid → 发 JWT**：

```
小游戏 wx.login() → code
   ↓ POST /api/auth/wx { code }
后端：code2session 换 openid（或直接读云托管注入的 X-WX-OPENID）
   ↓
按 openid 找账号，没有就自动建一个「矿工XXXX」→ 返回 JWT
```

| 环境变量 | 说明 |
| --- | --- |
| `WX_APPID` | 小游戏 AppID（走 code 校验时必填） |
| `WX_SECRET` | 小游戏 AppSecret（同上） |
| `WX_TRUST_OPENID` | 云托管填 `true`：直接信任平台注入的 `X-WX-OPENID`，**不用 AppSecret** |

> 云托管部署时 `container.config.json` 已经把 `WX_TRUST_OPENID` 设成 `true`，
> 所以只要通过 `wx.cloud.callContainer` 调用，登录就是开箱即用的。
> 自建服务器（`wx.request`）没有平台注入的头，必须配 `WX_APPID` / `WX_SECRET`。

数据库：`V5__wx_login.sql` 给 `users` 表加了 `openid`（唯一索引），密码字段改为可空；
老账号（账号密码注册的）不受影响，只是不能再用来登录新客户端。

### 常见问题：`PKIX path building failed`

容器的 JDK 用的是自己的 `cacerts`，和系统 `/etc/ssl/certs` 不是同一份。镜像里没带对应根证书时，
访问 `https://api.weixin.qq.com` 会报：

```
SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException:
unable to find valid certification path to requested target
```

`Dockerfile` 已经在构建时把系统根证书导进 JVM 信任库（`keytool -importcert -cacerts`）。
如果仍然失败（比如出口被代理拦截），可以临时在服务设置里加：

```
WX_INSECURE_TLS=true
```

现在默认就是「校验失败自动降级重试一次」，日志会打印 `证书校验失败，已自动降级重试`。
要恢复严格模式：`WX_INSECURE_TLS=false`（这时证书有问题就直接报错，不会降级）。
