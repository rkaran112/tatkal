# Deploy to AWS EC2 (one small server, docker compose)

Goal: the same `docker compose up` you run on your laptop, on one AWS server that anyone can open in a browser.

**Cost.** AWS accounts created after 15 July 2025 get a *Free plan*: up to $200 of credits that last 6 months at most. When the credits or the 6 months run out, the account is closed and you are not billed. Older accounts are on the previous 12-month free tier, which may already have ended. Check what your account shows under **Billing > Free Tier** before you start.

Everything below is done by you in the AWS console. Do not paste your AWS keys or passwords into any chat.

## 1. Before you launch

1. Create (or open) your AWS account and choose the **Free plan**.
2. In **Billing > Budgets**, create a budget alert (for example $5) that emails you. This is a safety net.
3. Pick a region close to you (Mumbai, `ap-south-1`).

## 2. Launch the server

In **EC2 > Launch instance**:

| Setting | Value |
|---|---|
| Image | Ubuntu Server 24.04 LTS |
| Instance type | One marked **Free tier eligible** in the list. Prefer one with **2 GB of memory or more** (Spring Boot + PostgreSQL + Redis + nginx are tight in 1 GB). |
| Key pair | Create one, download the `.pem` file, keep it private |
| Storage | 20 GB gp3 |
| Security group | **SSH (22) from "My IP" only. HTTP (80) from anywhere.** Nothing else. |

Do **not** open ports 8080, 5432 or 6379 to the internet. The database and Redis have the demo password `tatkal`, and port 8080 exposes the admin reset endpoint.

## 3. Install Docker on the server

Connect (replace the address with your instance's public IP):

```
ssh -i path\to\key.pem ubuntu@<public-ip>
```

Then on the server:

```
sudo apt update
sudo apt install -y docker.io docker-compose-v2 git openjdk-21-jdk-headless
sudo usermod -aG docker ubuntu
```

Log out and back in once so the `docker` group applies.

If the instance has only 1 GB of memory, add swap before building:

```
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
```

## 4. Get the code and start it

```
git clone https://github.com/<your-account>/tatkal.git
cd tatkal
docker compose up -d --build
```

The first build takes a few minutes. Check it:

```
curl -s http://localhost:8080/actuator/health
```

Then open `http://<public-ip>` in your browser. You should see the seat map.

## 5. Run the race on the server (optional but nice for the README)

```
bash loadtest/run-all.sh
cp docs/results/results.csv docs/results/results-ec2.csv
```

Copy `results-ec2.csv` back to your laptop with `scp` and add a short "on EC2" table to the README. Say plainly that the load test ran on the same small machine as the app.

## 6. Switch it off when you are done

An idle server still uses your credits. When the demo period is over, **terminate the instance** in the EC2 console (stop is not enough for the disk). Delete the budget too if you like.

## What this setup does not have

- No HTTPS or domain name. It is plain `http://` on the server's IP. (A small reverse proxy such as Caddy would add HTTPS later.)
- No automatic restarts after a server reboot beyond Docker's defaults, no backups, one server only. Fine for a portfolio demo.
