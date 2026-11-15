# Running Mentalist on a public server

One small server runs everything (app, database, queue, embeddings) behind
HTTPS. People create an account, upload their own reports (private to them)
and see the sample reports you add. Each account gets a daily allowance of AI
calls (3 report analyses and 30 questions by default), and the whole site has
a daily ceiling too, so the bill stays predictable.

**What it costs:** €0 on Oracle Cloud's Always Free tier with a free DuckDNS
address, or about €6–15/month on Hetzner. AI: €0 on Gemini's free tier, or a
few euros a month on the paid tier with a budget cap (recommended: on the free
tier Google may use what people send to improve its products).

Worst case with the default ceilings (60 analyses and 900 questions a day,
every day) is roughly €5–15 a day on Gemini Flash; real use is usually far
lower. A Google budget cap (step 3) is your safety net.

---

## 1. Get a server

You need Ubuntu 24.04, **at least 4 GB RAM (8 GB or more is comfortable)**,
and ports 80 and 443 open to the internet.

### Option A: Oracle Cloud Always Free (€0)

1. Sign up at cloud.oracle.com (a card is needed for verification; Always Free
   resources are not charged).
2. **Create instance** → Image: *Canonical Ubuntu 24.04* → Shape:
   *VM.Standard.A1.Flex* (Ampere, ARM) with **2 OCPUs and 12 GB**. Add your
   SSH public key. If it says "out of capacity", try another availability
   domain or try again later.
3. Open the ports in **two** places:
   - Networking → your VCN → *Security list* → add ingress rules for TCP 80
     and 443 from `0.0.0.0/0`.
   - On the server itself (Oracle's Ubuntu images block them with iptables):
     ```bash
     sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
     sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
     sudo netfilter-persistent save
     ```

### Option B: Hetzner Cloud (from about €6/month, EU)

Create a server with Ubuntu 24.04 and 8 GB RAM if your budget allows (4 GB
works with the swap file below). Add your SSH key. Ports are open by default.

On a 4 GB server, add swap so builds don't run out of memory:
```bash
sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## 2. Give it a name

- **Free:** at duckdns.org sign in, create a subdomain (e.g. `mentalist`)
  and set its IP to your server's public IP → `mentalist.duckdns.org`.
- **Own domain:** add an `A` record pointing to the server's IP.

Check with `ping mentalist.duckdns.org` that it resolves to your server.

## 3. Prepare the AI key

1. Create a key at https://aistudio.google.com/apikey.
2. Recommended: enable billing on that Google Cloud project and set a
   **budget** (e.g. €5/month) with alerts. Paid-tier requests are not used to
   train Google's models.

## 4. Install and start

**Quickest:** SSH into the server and paste this one line. It opens the
ports (Oracle), adds swap on small servers, installs Docker, downloads
Mentalist, asks for your domain, Gemini key and admin password, then builds
and starts everything and tells you when it's live:

```bash
bash <(curl -fsSL https://raw.githubusercontent.com/Shreya4119/mentalist/main/deploy/public/setup-server.sh)
```

**Or step by step:** SSH into the server, then:

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && newgrp docker

git clone https://github.com/Shreya4119/mentalist.git
cd mentalist
./deploy/public/make-env.sh          # domain, Gemini key, admin password; generates the other secrets
docker compose -f docker-compose.public.yml up -d --build
```

The first build takes 5–15 minutes. Watch progress with
`docker compose -f docker-compose.public.yml logs -f backend`; it's ready when
you see `Started BackendApplication`. Caddy fetches the HTTPS certificate on
the first visit.

If the backend stops with "PUBLIC_MODE is on but the server still uses
development settings", a value in `.env` is missing or too short.

## 5. Add the sample reports

1. Open `https://your-domain` and sign in as `admin` with your password.
2. Upload 3–5 public annual reports (e.g. Adyen, ASML, Philips, a German
   company). Everything the admin uploads is shared with all users as a
   **Sample** report. Each analysis takes a minute or two.
3. Sign out, click **Create account** and try it as a new user would.

## Day-to-day

| Task | Command (in the `mentalist` folder) |
|---|---|
| Update to the latest code | `git pull && docker compose -f docker-compose.public.yml up -d --build` |
| See logs | `docker compose -f docker-compose.public.yml logs -f backend` |
| Restart | `docker compose -f docker-compose.public.yml restart` |
| Back up the database | `docker compose -f docker-compose.public.yml exec db pg_dump -U mentalist mentalist > backup.sql` |
| Change limits or passwords | edit `.env`, then `docker compose -f docker-compose.public.yml up -d` |
| Stop everything | `docker compose -f docker-compose.public.yml down` (data is kept in volumes) |

## Built-in protections

- **Private reports:** each person sees only their own uploads plus the
  admin's sample reports. Someone else's report answers "not found", even by
  direct link, and duplicate detection never reveals another person's file.
- **Daily allowance** (reset at midnight, Amsterdam time): 3 analyses and 30
  questions per person; 60 analyses and 900 questions for the whole site.
  The admin is not limited. Change the numbers in `.env`.
- **Accounts:** username + password only (no email). Passwords must be at
  least 10 characters and not easy to guess. At most 5 new accounts per
  address per hour, and 1,000 accounts in total (`MAX_USERS`). Set
  `SIGNUP_ENABLED=false` to stop new sign-ups at any time.
- **Account deletion:** people can delete their account with all their
  reports, files and health checks from *Your account*.
- **Sign-in:** 10 failed attempts per address per 15 minutes.
- **The backend refuses to start** with the development secrets from GitHub.
- Only Caddy (80/443) is exposed; the database, queue, Ollama and metrics
  stay on Docker's private network.

## Troubleshooting

- **Browser says the site can't be reached:** the domain doesn't point to the
  server yet, or ports 80/443 are closed (on Oracle, check both the security
  list and iptables).
- **Certificate errors:** `docker compose -f docker-compose.public.yml logs caddy`.
  Let's Encrypt needs port 80 reachable and the domain resolving to this server.
- **Chat says the report isn't ready / no answers:** check that the embedding
  model was downloaded: `docker compose -f docker-compose.public.yml logs ollama-pull`.
- **Analyses fail with a quota or rate-limit message:** the Gemini key's free
  tier limits were reached; wait, or enable billing.
