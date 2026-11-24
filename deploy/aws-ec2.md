# Put Tatkal on the internet (AWS EC2, one small server)

Goal: a public web address anyone can open, running the same `docker compose` setup you run on your laptop.
You do the AWS console steps yourself (an account, a server, a few clicks). Everything on the server sets itself up from one pasted script, [`bootstrap.sh`](bootstrap.sh). You never type a command on the server.

**Cost.** AWS accounts created after 15 July 2025 get a *Free plan*: up to $200 of credits for at most 6 months. When the credits or the 6 months run out, the account closes and you are not billed. Older accounts are on the previous 12-month free tier, which may have ended. Check **Billing > Free Tier** for what your account shows. Do not paste your AWS password, keys or card details into any chat.

## 1. Before you launch (5 minutes)

1. Create or open your AWS account and choose the **Free plan**.
2. **Billing > Budgets**: create a budget alert (for example $5) that emails you.
3. Pick a region near you, such as Mumbai (`ap-south-1`).

## 2. Launch the server (10 minutes)

**EC2 > Launch instance**, then:

| Setting | Value |
|---|---|
| Name | `tatkal` |
| Image | **Ubuntu Server 24.04 LTS** |
| Instance type | Any type marked **Free tier eligible** with **2 GB of memory or more** if one is offered. (1 GB can work because the script adds swap, but the first build is slow.) |
| Key pair | **Proceed without a key pair**. You will not need to log in. |
| Network settings | Create a security group that allows **HTTP (port 80) from anywhere**. Do **not** allow SSH, and open nothing else. |
| Storage | 20 GB gp3 |
| **Advanced details > User data** | Open [`deploy/bootstrap.sh`](bootstrap.sh), copy the **whole file**, paste it here |

Click **Launch instance**.

## 3. Wait, then open it

The server installs Docker, downloads the code from GitHub, builds the Java app and the web page, and starts everything. The first time this takes **about 5 to 10 minutes**.

When the instance shows *Running*, copy its **Public IPv4 address** and open `http://<that address>` in a browser. Use `http`, not `https`. If it does not load yet, wait a few minutes and refresh.

**If it still does not appear after 15 minutes:** in the EC2 console, select the instance, then **Actions > Monitor and troubleshoot > Get system log**. The end of that log shows what the setup script was doing and where it failed. Copy the last 40 lines and send them to me.

## 4. What the public demo does

- Anyone can hold and book seats, so the seats would stay booked forever. The script adds a job that **frees all seats every 15 minutes**, and the page says so.
- The helper endpoints for the load test (`/api/admin/...`) answer 404 from the internet, and the backend port is open to the server itself only. The database and Redis are not reachable from outside either.

## 5. Optional: run the race on the server

You would need a way to log in (SSH or EC2 Instance Connect), which this setup deliberately leaves closed. The race results in the README were measured on a laptop. If you want server numbers too, ask me and we will open a login for your IP only.

## 6. Switch it off when you are done

An idle server uses your credits. When the demo period is over, **terminate the instance** (stopping it still bills for the disk) and delete the budget if you like.

## What this does not have

- No HTTPS and no domain name: it is plain `http://` on the server's IP. A domain and HTTPS can be added later with a small reverse proxy.
- No backups and only one server. That is fine for a portfolio demo.
- The address is a bare IP. If the instance is stopped and started, the IP changes unless you attach an **Elastic IP** (free while attached to a running instance).
