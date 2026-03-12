# 网页搜索（Web Search）配置说明

检索专家的 **searchWeb** 工具通过 [SearchAPI.io](https://www.searchapi.io/) 调用百度等搜索引擎，在知识库无结果或用户要求“上网查”时补充信息。

## 一、申请 API Key

1. **打开官网**  
   https://www.searchapi.io/

2. **注册 / 登录**  
   点击 **Sign Up** 或 **Login**，用邮箱或 Google/GitHub 登录。

3. **获取 API Key**  
   - 登录后进入 **Dashboard**  
   - 在 **API Key** 或 **Credentials** 区域复制你的 Key（形如 `abc123...`）

4. **计费说明**  
   SearchAPI.io 有免费额度（具体以官网为准），超出后按量计费。百度引擎需在 Dashboard 中启用。

## 二、在代码/配置里填写

项目读取的配置键为：**`search-api.api-key`**。

### 方式 1：环境变量（推荐，不落库）

```bash
export SEARCH_API_KEY="你的 SearchAPI.io API Key"
```

然后启动应用，无需改任何配置文件。

### 方式 2：本地配置文件（仅本地开发）

在 **`src/main/resources/application-local.yaml`**（该文件已在 .gitignore，不会提交）中增加：

```yaml
search-api:
  api-key: "你的 SearchAPI.io API Key"
```

若没有该文件，可复制 `application-local.yaml.example` 为 `application-local.yaml`，再按上述填写。

### 方式 3：主配置里写占位（不推荐写死 Key）

主配置 **`application.yaml`** 中已预留：

```yaml
search-api:
  api-key: "${SEARCH_API_KEY:}"
```

即默认从环境变量 `SEARCH_API_KEY` 读；若未设置则为空，searchWeb 调用会报错。**不要在此文件里写真实 Key**，以免误提交到 Git。

## 三、当前实现说明

- **接口**：`https://www.searchapi.io/api/v1/search`  
- **引擎**：`engine=baidu`（在 `WebSearchTool.java` 中写死，可改为 Google 等需在 SearchAPI 支持范围内）  
- **未配置时**：`apiKey` 为空，请求会失败，工具返回 `"Error searching Baidu: ..."`；若希望未配置时直接禁用工具，可后续在 `ToolRegistration` 里根据 key 是否为空决定是否注册 `WebSearchTool`。

## 四、验证是否生效

1. 配置好 Key 并启动应用。  
2. 问一个知识库没有的问题并明确要求上网查，例如：「介绍一下字节跳动，你要上网搜索最新的概念。」  
3. 查看日志中是否有 **`[AgentTrace.Tool]`** 或工具调用的 searchWeb 请求；若返回了百度搜索结果片段，说明配置成功。
