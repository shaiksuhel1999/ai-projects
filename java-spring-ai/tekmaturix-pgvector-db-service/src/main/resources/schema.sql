CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================================================
-- Main vector_store table required by Spring AI PgVectorStore
-- =========================================================
CREATE TABLE IF NOT EXISTS vector_store (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    metadata JSONB,
    embedding VECTOR(1024)
);

-- Faster similarity search index
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
ON vector_store
USING hnsw (embedding vector_cosine_ops);

-- =========================================================
-- Product details table
-- =========================================================
CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id INTEGER UNIQUE,
    name VARCHAR(255),
    brand VARCHAR(100),
    category VARCHAR(100),
    price VARCHAR(50),
    features TEXT,
    embedding VECTOR(1024)
);

CREATE INDEX IF NOT EXISTS products_embedding_idx
ON products
USING hnsw (embedding vector_cosine_ops);