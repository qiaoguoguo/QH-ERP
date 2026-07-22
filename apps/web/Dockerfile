FROM node:22.17.0-alpine3.22@sha256:fc3e945f920b7e3000cd1af86c4ae406ec70c72f328b667baf0f3a8910d69eed AS build

WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.29.0@sha256:93230cd54060f497430c7a120e2347894846a81b6a5dd2110f7362c5423b4abc

ARG QHERP_SOURCE_COMMIT=unknown
LABEL org.opencontainers.image.title="QH ERP Web" \
      org.opencontainers.image.revision="${QHERP_SOURCE_COMMIT}"

COPY nginx.production.conf /etc/nginx/nginx.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
