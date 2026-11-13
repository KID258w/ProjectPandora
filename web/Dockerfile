# Pandora Web 管理端生产镜像：Vite 构建 + Nginx 静态托管与反向代理
# 构建上下文固定为 web/，由 .github/workflows/cd-deploy.yml 调用。
FROM node:22-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# 生产环境与后端同域：留空表示请求走相对路径（/api/v1/...），由 Nginx 反代到 backend。
# 需要跨域部署时在仓库 Variables 里配置 VITE_API_BASE_URL。
ARG VITE_API_BASE_URL=
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

RUN npm run build


FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
