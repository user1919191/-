CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS embeddings (
    id BIGSERIAL PRIMARY KEY,
    embedding vector(1536),
    text TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS embeddings_vector_idx
ON embeddings
USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);

CREATE TABLE IF NOT EXISTS rag_parent_chunks (
    parent_id VARCHAR(64) PRIMARY KEY,
    document_name VARCHAR(255) NOT NULL,
    section_title VARCHAR(500),
    content TEXT NOT NULL,
    chunk_order INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS rag_parent_chunks_document_idx
ON rag_parent_chunks (document_name, chunk_order);
