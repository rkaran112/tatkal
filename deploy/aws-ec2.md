# Put Tatkal on the internet (AWS EC2, one small server)

Goal: a public web address anyone can open. You do the AWS console steps yourself (an account, a server, a few clicks). Everything on the server sets itself up from one pasted script, [`bootstrap.sh`](bootstrap.sh). You never type a command on the server.

## What it costs

Rough monthly cost for one server running all the time in Mumbai (prices from third-party price lists, so treat them as estimates):

| Item | About |
|---|---|
| `t3.micro` server (1 GB), $0.0112 per hour | $8.20 |
| Public IPv4 address, $0.005 per hour | $3.65 |
| 10 GB disk | under $1 |
| **Total** | **about $12.50 per month** |

On the **Free plan**, your AWS credits (up to $200, for at most 6 months) pay for this. Six months is about **$75**, so it fits, but leaves little spare, so do not run anything else. When the credits or the 6 months run out, the account closes and you are not billed. **Never click "Upgrade to a paid plan".** Budgets only send emails; they do not stop anything.

Two guards are built in so a forgotten server cannot drain your credits:
- The server **stops itself after 170 days** (a timer set by the script). A stopped server costs almost nothing. Start it again from the console if you want it back (its address will change).
- You set the CPU credit mode to **Standard** at launch (see below), so a busy server slows down instead of billing extra.

Do not enter your AWS password, keys or card details into any chat.

## 1. Before you launch

1. Create or open your AWS account and choose the **Free plan**. Check **Billing > Free Tier** shows your credits and expiry date.
2. **Billing > Budgets**: create a budget alert and also a **Zero spend budget**, so you get an email the moment anything is charged.
3. Pick a region near you, such as Mumbai (`ap-south-1`).

## 2. Launch the server

**EC2 > Launch instance**, then:

| Setting | Value |
|---|---|
| Name | `tatkal` |
| Image | **Ubuntu Server 24.04 LTS** |
| Instance type | **`t3.micro`** (1 GB, marked Free tier eligible) |
| Key pair | **Proceed without a key pair**. You will not need to log in. |
| Network settings | A security group that allows **HTTP (port 80) from anywhere** and **nothing else** (no SSH). Leave the default public IP setting on. Do **not** add an Elastic IP: an unused one is billed. |
| Storage | **10 GB** gp3 |
| **Advanced details > Credit specification** | **Standard** (the default for t3 is "Unlimited", which can bill extra) |
| **Advanced details > User data** | Open [`deploy/bootstrap.sh`](bootstrap.sh) on GitHub, copy the **whole file**, paste it here |

Click **Launch instance**.

## 3. Wait, then open it

The server installs Docker, downloads two ready-made images (about 400 MB) and starts everything. The first boot takes **about 5 to 10 minutes**, and the Java app needs about 30 more seconds to wake up on this small machine.

When the instance shows *Running*, copy its **Public IPv4 address** and open `http://<that address>`. Use `http`, not `https`.

**If it does not appear after 15 minutes:** select the instance, then **Actions > Monitor and troubleshoot > Get system log**. Copy the last 40 lines and send them to me.

## 4. What the public demo does

- Anyone can hold and book seats, so a job **frees all seats every 15 minutes**, and the page says so.
- The helper endpoints for the load test (`/api/admin/...`) answer 404 from the internet. The backend port, the database and Redis are reachable from the server only.
- The memory is tuned for 1 GB: Java gets a 256 MB heap, Postgres and Redis have small limits. On my laptop the app used about 335 MB after 1,000 simultaneous users. It has not been tried on a real 1 GB server yet; if the site is slow or keeps restarting, tell me and we will move up one size (about $10 more per month).

## 5. Switch it off when you are done

**Terminate** the instance in the EC2 console (stopping still bills a little for the disk).

## What this does not have

- No HTTPS or domain name: plain `http://` on the server's IP. The address changes if the server is stopped and started.
- No backups and only one server. Fine for a portfolio demo.
- The race results in the README were measured on a laptop, not on this server.
