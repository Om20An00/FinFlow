-- Database-per-service: every microservice owns its own database and its own login.
-- (One Postgres container for the demo; in production these would be separate instances / RDS databases.)
CREATE USER users_svc   WITH PASSWORD 'users_pw';
CREATE USER wallet_svc  WITH PASSWORD 'wallet_pw';
CREATE USER payment_svc WITH PASSWORD 'payment_pw';
CREATE USER audit_svc   WITH PASSWORD 'audit_pw';

CREATE DATABASE users_db   OWNER users_svc;
CREATE DATABASE wallet_db  OWNER wallet_svc;
CREATE DATABASE payment_db OWNER payment_svc;
CREATE DATABASE audit_db   OWNER audit_svc;

REVOKE ALL ON DATABASE users_db, wallet_db, payment_db, audit_db FROM PUBLIC;
