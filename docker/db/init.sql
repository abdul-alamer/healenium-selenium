-- Runs once, the first time the Postgres data directory is initialised.
-- The Healenium backend creates its own tables inside this schema on start-up; all it needs from
-- us is the schema itself and the right to use it.
CREATE SCHEMA healenium AUTHORIZATION healenium_user;
GRANT USAGE ON SCHEMA healenium TO healenium_user;
