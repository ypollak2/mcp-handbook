/**
 * Creates and seeds a sample SQLite database for testing.
 * Run: npx tsx src/seed.ts
 */
import Database from "better-sqlite3";
import { resolve } from "path";

const DB_PATH = resolve(import.meta.dirname, "../sample.db");
const db = new Database(DB_PATH);

db.exec(`
  CREATE TABLE IF NOT EXISTS products (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    category TEXT NOT NULL,
    price REAL NOT NULL,
    stock INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
  );

  CREATE TABLE IF NOT EXISTS orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id INTEGER NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL,
    total REAL NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    ordered_at TEXT NOT NULL DEFAULT (datetime('now'))
  );
`);

const insertProduct = db.prepare(
  "INSERT INTO products (name, category, price, stock) VALUES (?, ?, ?, ?)"
);

const insertOrder = db.prepare(
  "INSERT INTO orders (product_id, quantity, total, status, ordered_at) VALUES (?, ?, ?, ?, ?)"
);

const products: Array<[string, string, number, number]> = [
  ["Mechanical Keyboard", "Electronics", 149.99, 45],
  ["USB-C Hub", "Electronics", 59.99, 120],
  ["Standing Desk", "Furniture", 499.99, 12],
  ["Ergonomic Chair", "Furniture", 349.99, 8],
  ["Monitor Light Bar", "Electronics", 89.99, 67],
  ["Desk Mat", "Accessories", 29.99, 200],
  ["Webcam HD", "Electronics", 79.99, 34],
  ["Noise Cancelling Headphones", "Electronics", 299.99, 22],
  ["Cable Management Kit", "Accessories", 19.99, 150],
  ["Laptop Stand", "Accessories", 44.99, 88],
];

const statuses = ["pending", "shipped", "delivered"];

const seedAll = db.transaction(() => {
  for (const [name, category, price, stock] of products) {
    insertProduct.run(name, category, price, stock);
  }

  for (let i = 0; i < 25; i++) {
    const productId = Math.floor(Math.random() * 10) + 1;
    const quantity = Math.floor(Math.random() * 5) + 1;
    const price = products[productId - 1][2];
    const total = price * quantity;
    const status = statuses[Math.floor(Math.random() * statuses.length)];
    const daysAgo = Math.floor(Math.random() * 30);
    const date = new Date(Date.now() - daysAgo * 86400000).toISOString();
    insertOrder.run(productId, quantity, total, status, date);
  }
});

seedAll();

console.log(`Database seeded at ${DB_PATH}`);
console.log(`  - ${products.length} products`);
console.log("  - 25 orders");

db.close();
