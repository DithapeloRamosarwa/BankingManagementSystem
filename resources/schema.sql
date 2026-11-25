PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS customers (
  customer_id INTEGER PRIMARY KEY AUTOINCREMENT,
  first_name TEXT NOT NULL,
  last_name TEXT NOT NULL,
  address TEXT NOT NULL,
  phone TEXT NOT NULL,
  employed INTEGER NOT NULL DEFAULT 0 CHECK (employed IN (0,1))
);

CREATE TABLE IF NOT EXISTS users (
  user_id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT NOT NULL UNIQUE COLLATE NOCASE,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('CUSTOMER','TELLER','ADMIN')),
  customer_id INTEGER UNIQUE REFERENCES customers(customer_id),
  active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0,1)),
  CHECK ((role = 'CUSTOMER') = (customer_id IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS accounts (
  account_number TEXT PRIMARY KEY,
  customer_id INTEGER NOT NULL REFERENCES customers(customer_id),
  type TEXT NOT NULL CHECK (type IN ('SAVINGS','INVESTMENT','CHEQUE')),
  branch TEXT NOT NULL,
  balance_cents INTEGER NOT NULL DEFAULT 0 CHECK (balance_cents >= 0),
  employer_name TEXT,
  employer_address TEXT,
  opened_at TEXT NOT NULL,
  CHECK (type <> 'CHEQUE' OR (employer_name IS NOT NULL AND employer_address IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS transactions (
  transaction_id INTEGER PRIMARY KEY AUTOINCREMENT,
  account_number TEXT NOT NULL REFERENCES accounts(account_number),
  type TEXT NOT NULL CHECK (type IN ('OPEN','DEPOSIT','WITHDRAWAL','INTEREST')),
  amount_cents INTEGER NOT NULL CHECK ((type = 'OPEN' AND amount_cents >= 0) OR (type <> 'OPEN' AND amount_cents > 0)),
  balance_after_cents INTEGER NOT NULL CHECK (balance_after_cents >= 0),
  note TEXT NOT NULL DEFAULT '',
  occurred_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS interest_runs (
  account_number TEXT NOT NULL REFERENCES accounts(account_number),
  interest_month TEXT NOT NULL,
  PRIMARY KEY (account_number, interest_month)
);

CREATE INDEX IF NOT EXISTS idx_accounts_customer ON accounts(customer_id);
CREATE INDEX IF NOT EXISTS idx_transactions_account ON transactions(account_number, transaction_id DESC);
