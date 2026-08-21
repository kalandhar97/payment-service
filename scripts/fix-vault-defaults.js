const fs = require("fs");
const path = require("path");
const ROOT = path.resolve(__dirname, "..");
const services = [
  "gateway-service",
  "authentication-service",
  "user-service",
  "merchant-service",
  "tokenization-service",
  "limit-service",
  "authorization-service",
  "payment-service",
  "fraud-service",
  "clearing-service",
  "dispute-service",
  "settlement-service",
  "ledger-service",
  "reconciliation-service",
  "notification-service",
  "audit-service",
  "reporting-service",
];
for (const s of services) {
  const f = path.join(ROOT, s, "src", "main", "resources", "application-vault.yml");
  if (!fs.existsSync(f)) continue;
  let t = fs.readFileSync(f, "utf8");
  t = t.replace("VAULT_ENABLED:false", "VAULT_ENABLED:true");
  fs.writeFileSync(f, t);
  console.log("updated", s);
}
