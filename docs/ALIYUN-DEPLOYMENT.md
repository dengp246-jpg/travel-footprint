# Alibaba Cloud Ubuntu deployment

Target server: Ubuntu 22.04, 2 vCPU, 2 GiB RAM, 40 GiB ESSD, public IPv4. The application uses Docker Compose with Caddy. H2 data and uploaded media remain on the host under `deploy/aliyun/runtime`.

## Network rules

Allow inbound TCP `22`, `80`, and `443` in the Alibaba Cloud firewall or security group. Restrict SSH `22` to the administrator IP when practical. UDP `443` is optional for HTTP/3.

## Install Docker

```bash
apt-get update
apt-get install -y ca-certificates curl git
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
. /etc/os-release
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $VERSION_CODENAME stable" > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

For a 2 GiB server, add a 2 GiB swap file before the first Maven/Docker build:

```bash
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

## Deploy

```bash
git clone https://github.com/dengp246-jpg/travel-footprint.git /opt/travel-footprint
cd /opt/travel-footprint
cp deploy/aliyun/.env.example deploy/aliyun/.env
nano deploy/aliyun/.env
./scripts/deploy-aliyun.sh
```

Use `SITE_ADDRESS=http://PUBLIC_IP` only for initial testing. A mainland China server requires an ICP-filed domain before normal public website operation. After the domain points to the fixed IPv4 address, change `SITE_ADDRESS` to the domain name and rerun the deployment script; Caddy will request and renew HTTPS automatically.

## Existing local data

Stop the local application before copying H2 files. Place the local `data/` contents into `deploy/aliyun/runtime/data/` and local `uploads/` contents into `deploy/aliyun/runtime/uploads/` before starting the cloud application. Do not copy H2 lock files.

## Operations

```bash
docker compose --env-file deploy/aliyun/.env -f deploy/aliyun/docker-compose.yml ps
docker compose --env-file deploy/aliyun/.env -f deploy/aliyun/docker-compose.yml logs --tail=100
./scripts/backup-aliyun.sh
```
