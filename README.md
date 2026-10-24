# Picture Backend — 智能图片管理系统

基于 Spring Boot 2.7 和 Java 11 开发的智能图片管理系统后端，提供用户管理、图片上传、图片审核、公共图库、私人空间管理等功能。

项目采用前后端分离架构，后端通过 RESTful API 提供服务，前端基于 Vue 3、TypeScript 和 Ant Design Vue 构建交互界面。

## 一、项目介绍

Picture Backend 是一个面向图片资源存储、分类、检索与管理的后端服务。

系统支持普通用户管理个人图片资源，也支持管理员对平台用户和公共图库内容进行管理。

通过腾讯云 COS 实现图片文件存储，结合 MySQL、Redis、MyBatis-Plus 等技术，完成图片数据持久化、用户登录状态管理以及权限控制。

## 二、技术栈

| 技术 | 版本 / 说明 | 用途 |
|---|---|---|
| Java | JDK 11 | 后端开发语言 |
| Spring Boot | 2.7.18 | 后端应用框架 |
| Spring MVC | Spring Boot 集成 | RESTful API |
| MyBatis-Plus | 3.5.9 | 数据库访问与分页查询 |
| MySQL | 8.x | 业务数据持久化 |
| Redis | Redis + Spring Session | 用户登录会话管理 |
| 腾讯云 COS | 对象存储服务 | 图片文件存储 |
| Knife4j | 4.4.0 | API 接口文档 |
| Maven | Maven Wrapper | 项目构建与依赖管理 |
| Hutool | 5.8.26 | Java 工具类 |
| Lombok | — | 简化实体类开发 |

配套前端采用 Vue 3、TypeScript、Vite、Pinia、Vue Router 和 Ant Design Vue。

## 三、核心功能

### 1. 用户管理模块

- 用户注册与登录
- 用户退出登录
- 查询当前登录用户信息
- 用户信息管理
- 用户分页查询
- 管理员删除用户
- 用户角色与权限校验
- 基于 Redis 和 Spring Session 管理登录状态

### 2. 图片管理模块

**图片上传**

- 支持本地图片文件上传
- 支持通过图片 URL 上传
- 支持关键词批量获取并上传图片
- 使用腾讯云 COS 存储图片资源

**图片信息管理**

- 编辑图片名称
- 编辑图片简介
- 设置图片分类
- 设置图片标签
- 查询图片详情
- 分页查询图片列表
- 按搜索条件筛选图片
- 删除图片

**图片审核**

- 待审核
- 审核通过
- 审核拒绝
- 管理员执行图片审核
- 记录审核状态和审核信息
- 公共图库展示审核通过的图片

### 3. 私人空间管理模块

- 创建私人空间
- 查询空间详情
- 查询用户空间
- 编辑空间信息
- 删除空间
- 管理空间名称与级别
- 管理空间容量和图片数量限制
- 查询指定空间下的图片
- 上传图片时通过 `spaceId` 关联空间
- 根据用户身份控制空间和图片操作权限

### 4. 公共图库模块

- 展示已审核通过的公共图片
- 根据关键词搜索图片
- 根据分类和标签筛选图片
- 分页加载图片列表
- 查看图片详情

### 5. 权限控制模块

系统区分普通用户和管理员角色。

普通用户主要使用图片上传、查询和个人空间管理功能。

管理员可以使用用户管理、图片管理和图片审核等后台功能。

通过身份校验和业务权限判断，限制未授权操作。

## 四、项目结构

```text
picture-backend/
├── .mvn/                         # Maven Wrapper
├── sql/
│   └── create_table.sql          # 数据库建表脚本
├── src/
│   ├── main/
│   │   ├── java/                 # Java 源代码
│   │   │   └── .../
│   │   │       ├── annotation/   # 自定义注解
│   │   │       ├── aop/          # 权限校验切面
│   │   │       ├── common/       # 通用响应和请求对象
│   │   │       ├── config/       # 系统配置
│   │   │       ├── constant/     # 常量定义
│   │   │       ├── controller/   # API 控制器
│   │   │       ├── exception/    # 全局异常处理
│   │   │       ├── manager/      # 图片存储与上传管理
│   │   │       ├── mapper/       # 数据访问接口
│   │   │       ├── model/        # 实体、DTO、VO、枚举
│   │   │       └── service/      # 业务逻辑
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-local.yml
│   └── test/                     # 测试代码
├── .gitignore
├── pom.xml
└── README.md
```

## 五、运行环境

本地运行需要准备：

- JDK 11
- Maven
- MySQL 8.x
- Redis
- 腾讯云 COS 存储桶
- IntelliJ IDEA 或其他 Java IDE

### 1. 初始化数据库

在 MySQL 中创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS yu_picture
DEFAULT CHARACTER SET utf8mb4;
```

执行项目中的建表脚本：

```text
sql/create_table.sql
```

### 2. 配置环境变量

项目通过环境变量管理数据库连接信息及腾讯云 COS 访问凭证。

建议配置以下环境变量：

```env
MYSQL_URL=jdbc:mysql://localhost:3306/yu_picture
MYSQL_USERNAME=root
MYSQL_PASSWORD=your_mysql_password

COS_HOST=your_cos_host
COS_SECRET_ID=your_cos_secret_id
COS_SECRET_KEY=your_cos_secret_key
COS_REGION=ap-shanghai
COS_BUCKET=your_cos_bucket
```

以上变量名称需要与实际 `application.yml`、`application-local.yml` 中的占位符保持一致。

不要将真实数据库密码、云存储密钥上传到公开仓库。

### 3. 启动 Redis

确保本地 Redis 服务正常运行，默认连接地址为：

```text
127.0.0.1:6379
```

### 4. 启动 Spring Boot

在项目根目录执行：

```bash
./mvnw spring-boot:run
```

Windows PowerShell：

```powershell
.\mvnw.cmd spring-boot:run
```

也可以直接在 IDEA 中运行 Spring Boot 启动类。

### 5. 访问后端服务

默认端口：

```text
8124
```

API 根路径：

```text
http://localhost:8124/api
```

Knife4j 接口文档：

```text
http://localhost:8124/api/doc.html
```

## 六、数据库设计

系统主要围绕以下业务实体进行数据管理：

| 数据表 | 说明 |
|---|---|
| user | 用户信息、账号及角色 |
| picture | 图片信息、分类、标签、审核状态及所属空间 |
| space | 私人空间信息、容量及数量限制 |

使用 MyBatis-Plus 完成常见数据库操作，并通过逻辑删除机制管理业务数据。

## 七、系统设计特点

### 1. 前后端分离

后端统一提供 RESTful API，前端通过 HTTP 请求调用服务。

### 2. 对象存储

图片文件存储在腾讯云 COS，MySQL 保存图片相关业务信息。

### 3. 会话管理

使用 Redis 和 Spring Session 管理用户登录状态。

### 4. 统一异常处理

通过全局异常处理器返回统一的错误信息，便于前端进行异常提示。

### 5. 权限管理

通过角色校验、自定义注解和业务权限判断，限制未授权访问。

### 6. 分层架构

采用 Controller、Service、Mapper 分层设计，降低业务代码耦合度。

## 八、项目仓库

后端仓库：

https://github.com/ZiMing929/picture-backend

前端仓库：

https://github.com/ZiMing929/picture-frontend

## 九、后续优化方向

- 图片检索体验优化
- 图片批量管理
- 图片上传性能优化
- 图片存储成本优化
- 完善自动化测试
- 完善项目部署与运行监控

## 十、注意事项

1. 本项目需要自行配置 MySQL、Redis 和腾讯云 COS。
2. 本地配置文件和敏感密钥不得提交到公开仓库。
3. 请根据实际环境修改数据库、Redis 和 COS 连接参数。
4. 首次启动前，需要完成数据库初始化。
5. 部分功能需要登录或管理员权限才能使用。

---

**Picture Backend — 智能图片管理系统**

一个基于 Spring Boot 的前后端分离图片管理项目。
