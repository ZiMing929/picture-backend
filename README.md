智能云图库管理系统（Smart Cloud Picture Management System）
基于 Spring Boot 2.7 + Java 11 构建的智能云图库管理系统后端，提供图片上传、存储、检索、审核、用户管理和私人空间管理等功能。

项目采用前后端分离架构，后端通过 RESTful API 提供服务，前端基于 Vue 3、TypeScript 和 Ant Design Vue 实现交互界面。

项目仓库
- 后端（当前仓库）：picture-backend
- 前端：picture-frontend
  一、项目介绍
  智能云图库管理系统面向图片资源的上传、存储、分类、搜索与管理场景，支持公共图库和私人空间两类使用方式。

用户可以浏览公共图库、上传图片、查看图片详情；管理员可以管理用户、审核及管理图片；登录用户可以在自己的私人空间中管理图片资源。
二、技术栈
分类	技术
开发语言	Java 11
后端框架	Spring Boot 2.7
ORM 框架	MyBatis-Plus
数据库	MySQL
缓存 / 会话	Redis
对象存储	腾讯云 COS
API 文档	Knife4j
项目构建	Maven


三、主要功能
- 用户管理：用户注册、登录、个人信息获取及管理员管理功能。
- 公共图库：图片列表、分页查询、关键词搜索、分类与标签筛选。
- 图片上传：支持本地文件上传和 URL 上传，并获取图片基础信息。
- 图片处理：对接腾讯云 COS，支持图片处理结果及缩略图信息管理。
- 图片管理：图片详情、编辑、删除及管理员审核。
- 批量创建：根据搜索关键词批量获取图片并创建图库资源。
- 私人空间：创建私人空间，管理个人图片及空间资源配额。

  四、项目结构
  picture-backend/
  ├── src/
  │   └── main/
  │       ├── java/              # Java 后端业务代码
  │       └── resources/         # 配置及资源文件
  ├── sql/                       # 数据库脚本
  ├── pom.xml                    # Maven 依赖配置
  └── README.md
  五、本地运行
  环境要求：JDK 11、Maven、MySQL、Redis，以及已配置的腾讯云 COS 存储桶。
1. 克隆仓库：
   git clone https://github.com/ZiMing929/picture-backend.git
   cd picture-backend
2. 根据 sql/ 下的数据库脚本初始化数据库。
3. 配置本地 MySQL、Redis 连接信息及腾讯云 COS 参数。敏感配置请保存在未提交到 Git 的本地配置文件或环境变量中，不要将数据库密码、SecretId、SecretKey 提交到公开仓库。
4. 使用 IDE 启动 Spring Boot 主程序，或执行：
   ./mvnw spring-boot:run
   Windows PowerShell 可使用：
   .\mvnw.cmd spring-boot:run
   默认开发环境后端地址：http://localhost:8124/api（以实际配置为准）。
   六、前端项目
   配套前端采用 Vue 3 + TypeScript + Vite + Ant Design Vue + Pinia + Vue Router，提供公共图库、图片上传、图片详情、后台管理和私人空间等交互页面。

前端仓库：picture-frontend
本项目用于全栈开发学习、功能实践与项目展示，功能将持续完善。