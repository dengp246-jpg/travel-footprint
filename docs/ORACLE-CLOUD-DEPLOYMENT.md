# Oracle Cloud Always Free deployment

This deployment keeps the existing Spring Boot, H2, image, and video architecture. The application runs behind Caddy, which enables HTTPS automatically after a domain points to the VM. H2 data and uploads are bind-mounted under `deploy/oracle/runtime` so container replacement does not erase user data.

## 1. Create the free VM

1. Create or sign in to an Oracle Cloud account and select the home region carefully.
2. Open **Compute → Instances → Create instance**.
3. Use Ubuntu 24.04 and an **Always Free eligible** shape:
   - preferred: `VM.Standard.A1.Flex`, 1 OCPU and 2 GB RAM;
   - fallback: `VM.Standard.E2.1.Micro`.
4. Use a public subnet, assign a public IPv4 address, and paste the dedicated SSH public key.
5. In the subnet security list or Network Security Group, allow TCP ports `22`, `80`, and `443`. Allow UDP `443` for HTTP/3 if desired.
6. Reserve and assign a public IP before configuring the final domain, so the address survives instance replacement.

## 2. Connect and install Docker

From Windows PowerShell:

```powershell
ssh -i "$env:USERPROFILE\.ssh\oracle_travel_footprint" ubuntu@YOUR_PUBLIC_IP
```

On the VM:

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
. /etc/os-release
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $VERSION_CODENAME stable" | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp
sudo ufw --force enable
```

Sign out and reconnect once so the Docker group membership takes effect.

## 3. Deploy the application

```bash
git clone https://github.com/dengp246-jpg/travel-footprint.git ~/travel-footprint
cd ~/travel-footprint
cp deploy/oracle/.env.example deploy/oracle/.env
nano deploy/oracle/.env
chmod +x scripts/deploy-oracle.sh scripts/backup-oracle.sh
./scripts/deploy-oracle.sh
```

Set `SITE_ADDRESS` to `http://PUBLIC_IP` for the first connectivity test. For automatic HTTPS, point a domain `A` record to the reserved public IP, change `SITE_ADDRESS` to the domain without `http://`, and run `./scripts/deploy-oracle.sh` again.

Never commit `deploy/oracle/.env`. It contains the administrator bootstrap password and AMap configuration.

## 4. Verify and operate

```bash
curl -fsS https://YOUR_DOMAIN/health
docker compose --env-file deploy/oracle/.env -f deploy/oracle/docker-compose.yml ps
docker compose --env-file deploy/oracle/.env -f deploy/oracle/docker-compose.yml logs --tail=100
./scripts/backup-oracle.sh
```

From the Windows checkout:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-cloud-deployment.ps1 -ServerUrl "https://YOUR_DOMAIN"
```

## 5. Enable GitHub automatic deployment

In the GitHub repository add Actions secrets:

- `OCI_HOST`: reserved public IP or domain;
- `OCI_USER`: normally `ubuntu`;
- `OCI_SSH_KEY`: the complete dedicated private key.

Then add the repository variable `OCI_DEPLOY_ENABLED=true`. Future pushes to `main` will connect to the VM, fast-forward the checkout, rebuild the image, and restart the containers. Until this variable is enabled, the workflow remains safely skipped.

## Backups

Run `./scripts/backup-oracle.sh` before every risky update. Copy archives from `deploy/oracle/backups` to another machine or object storage. A backup stored only on the same VM is not disaster recovery.
