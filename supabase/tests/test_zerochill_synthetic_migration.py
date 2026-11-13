"""ZEROCHILL dry run with ONLY invented data and an in-memory SQLite database.

This checks identity collisions, owner relationships, rollback, and repeat imports.
It does NOT connect to Supabase, migrate passwords, test Auth, or touch real data.
Run: python3 -m unittest -v test_zerochill_synthetic_migration.py
"""
import sqlite3
import unittest

SCHEMA = """
PRAGMA foreign_keys=ON;
CREATE TABLE users(id TEXT PRIMARY KEY, email_key TEXT NOT NULL UNIQUE, fake_hash TEXT NOT NULL);
CREATE TABLE identities(user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider TEXT NOT NULL, PRIMARY KEY(user_id, provider));
CREATE TABLE profiles(user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    username_key TEXT NOT NULL UNIQUE);
CREATE TABLE creator_favorites(user_id TEXT NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    creator_key TEXT NOT NULL, PRIMARY KEY(user_id, creator_key));
CREATE TABLE video_likes(user_id TEXT NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    content_key TEXT NOT NULL, PRIMARY KEY(user_id, content_key));
CREATE TABLE comments(id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    parent_id TEXT REFERENCES comments(id) ON DELETE CASCADE, body TEXT NOT NULL);
CREATE TABLE comment_likes(comment_id TEXT REFERENCES comments(id) ON DELETE CASCADE,
    user_id TEXT REFERENCES profiles(user_id) ON DELETE CASCADE, PRIMARY KEY(comment_id,user_id));
CREATE TABLE direct_messages(id TEXT PRIMARY KEY, sender_id TEXT NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    recipient_id TEXT NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE, body TEXT NOT NULL,
    CHECK(sender_id<>recipient_id));
CREATE TABLE dm_reactions(message_id TEXT NOT NULL REFERENCES direct_messages(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE, emoji TEXT NOT NULL,
    PRIMARY KEY(message_id,user_id,emoji));
CREATE TABLE notification_preferences(user_id TEXT PRIMARY KEY REFERENCES profiles(user_id) ON DELETE CASCADE,
    dms_enabled INTEGER NOT NULL);
CREATE TABLE conversation_clear_state(user_id TEXT NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    partner_id TEXT NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    cutoff TEXT NOT NULL, PRIMARY KEY(user_id,partner_id), CHECK(user_id<>partner_id));
CREATE TABLE storage_objects(id TEXT PRIMARY KEY, owner_id TEXT REFERENCES profiles(user_id) ON DELETE CASCADE,
    bucket TEXT NOT NULL, object_path TEXT NOT NULL UNIQUE, fake_byte_digest TEXT NOT NULL);
"""
TABLES = (
    "users", "identities", "profiles", "creator_favorites", "video_likes", "comments",
    "comment_likes", "direct_messages", "dm_reactions", "notification_preferences",
    "conversation_clear_state", "storage_objects",
)
TEST_IDS = frozenset(("test-a", "test-b", "test-c"))
OG_IDS = frozenset(("og-a", "og-b", "og-c", "og-d"))


def database():
    db = sqlite3.connect(":memory:")
    db.row_factory = sqlite3.Row
    db.executescript(SCHEMA)
    return db


def fixture_source():
    db = database()
    with db:
        db.executemany("INSERT INTO users VALUES (?,?,?)", [
            ("og-a", "one@example.invalid", "fake-og-hash-a"),
            ("og-b", "two@example.invalid", "fake-og-hash-b"),
            ("og-c", "three@example.invalid", "fake-og-hash-c"),
            ("og-d", "four@example.invalid", "fake-og-hash-d"),
        ])
        db.executemany("INSERT INTO identities VALUES (?,?)", [(u, "email") for u in OG_IDS])
        db.executemany("INSERT INTO profiles VALUES (?,?)", [
            ("og-a", "original_a"), ("og-b", "original_b"),
            ("og-c", "original_c"), ("og-d", "testbot"),
        ])
        db.executemany("INSERT INTO creator_favorites VALUES (?,?)", [
            ("og-a", "creator-1"), ("og-b", "creator-2"),
        ])
        db.executemany("INSERT INTO video_likes VALUES (?,?)", [
            ("og-a", "video-1"), ("og-c", "video-2"),
        ])
        db.executemany("INSERT INTO comments VALUES (?,?,?,?)", [
            ("comment-1", "og-a", None, "fake parent"),
            ("comment-2", "og-d", "comment-1", "fake reply"),
        ])
        db.execute("INSERT INTO comment_likes VALUES (?,?)", ("comment-1", "og-b"))
        db.execute("INSERT INTO direct_messages VALUES (?,?,?,?)",
                   ("message-1", "og-a", "og-b", "fake OG DM"))
        db.execute("INSERT INTO dm_reactions VALUES (?,?,?)", ("message-1", "og-b", "heart"))
        db.execute("INSERT INTO notification_preferences VALUES (?,?)", ("og-a", 0))
        db.execute("INSERT INTO conversation_clear_state VALUES (?,?,?)",
                   ("og-b", "og-a", "2026-11-13T00:00:00Z"))
        db.execute("INSERT INTO storage_objects VALUES (?,?,?,?,?)",
                   ("obj-1", "og-a", "avatars", "og-a/avatar.png", "invented-bytes-only"))
    return db


def fixture_destination():
    db = database()
    with db:
        db.executemany("INSERT INTO users VALUES (?,?,?)", [
            ("test-a", "one@example.invalid", "fake-test-a"),
            ("test-b", "two@example.invalid", "fake-test-b"),
            ("test-c", "three@example.invalid", "fake-test-c"),
        ])
        db.executemany("INSERT INTO identities VALUES (?,?)", [(u, "email") for u in TEST_IDS])
        db.executemany("INSERT INTO profiles VALUES (?,?)", [
            ("test-a", "zerochill"), ("test-b", "testbot"),
        ])
        db.execute("INSERT INTO direct_messages VALUES (?,?,?,?)",
                   ("test-message", "test-a", "test-b", "fake test-only DM"))
    return db


def rows(db, table):
    """Table names must come only from the static TABLES tuple."""
    if table not in TABLES:
        raise ValueError("Unexpected table")
    return sorted(tuple(record) for record in db.execute(f"SELECT * FROM {table}"))


def colliding_ids(source, target):
    source_email = {r[1]: r[0] for r in source.execute("SELECT id,email_key FROM users")}
    dest_email = {r[1]: r[0] for r in target.execute("SELECT id,email_key FROM users")}
    return {dest_email[email] for email in source_email.keys() & dest_email.keys()}


def rehearsal_import(source, target, *, fail_after_table=None):
    """Synthetic-only replacement. Never call with a live database connection."""
    if target.execute("PRAGMA foreign_keys").fetchone()[0] != 1:
        raise RuntimeError("Foreign keys must stay enabled")
    # Refuse to alter any destination user beyond the three known fake test IDs.
    current = {r[0] for r in target.execute("SELECT id FROM users")}
    if not current.issubset(TEST_IDS | OG_IDS):
        raise RuntimeError("Unrecognized destination identity: fail closed")
    if not set(r[0] for r in source.execute("SELECT id FROM users")) == OG_IDS:
        raise RuntimeError("Unexpected source account set: fail closed")
    # The overlapping test identities are disposable in this ONLY-fake fixture.
    # On real Supabase, the operator must back them up and approve their removal.
    with target:
        for user_id in sorted(current & TEST_IDS):
            target.execute("DELETE FROM users WHERE id=?", (user_id,))
        for table in TABLES:
            columns = [r[1] for r in source.execute(f"PRAGMA table_info({table})")]
            placeholders = ",".join("?" for _ in columns)
            insert = f"INSERT INTO {table} VALUES ({placeholders}) ON CONFLICT DO NOTHING"
            data = rows(source, table)
            # The real import must topologically order arbitrarily nested replies.
            if table == "comments":
                data = sorted(data, key=lambda row: (row[2] is not None, row[0]))
            target.executemany(insert, data)
            if fail_after_table == table:
                raise RuntimeError("simulated import failure")
        if target.execute("PRAGMA foreign_key_check").fetchall():
            raise RuntimeError("Broken ownership or foreign key")
        for table in TABLES:
            if rows(target, table) != rows(source, table):
                raise RuntimeError("Missing, duplicated, or changed row in " + table)


class SyntheticMigrationTests(unittest.TestCase):
    def setUp(self):
        self.source = fixture_source()
        self.target = fixture_destination()

    def tearDown(self):
        self.source.close()
        self.target.close()

    def test_three_emails_collide_but_old_ids_are_distinct(self):
        self.assertEqual(colliding_ids(self.source, self.target), TEST_IDS)
        old_names = {r[1]: r[0] for r in self.source.execute("SELECT user_id,username_key FROM profiles")}
        new_names = {r[1]: r[0] for r in self.target.execute("SELECT user_id,username_key FROM profiles")}
        self.assertEqual(old_names["testbot"], "og-d")
        self.assertEqual(new_names["testbot"], "test-b")
        self.assertNotEqual(old_names["testbot"], new_names["testbot"])

    def test_og_uuid_and_all_social_history_survive(self):
        source_before = {t: rows(self.source, t) for t in TABLES}
        rehearsal_import(self.source, self.target)
        self.assertEqual({r[0] for r in self.target.execute("SELECT id FROM users")}, OG_IDS)
        for t in TABLES:
            self.assertEqual(rows(self.target, t), source_before[t], t)
            self.assertEqual(rows(self.source, t), source_before[t], "source changed: " + t)
        self.assertEqual(self.target.execute("SELECT count(*) FROM direct_messages WHERE id='test-message'").fetchone()[0], 0)
        self.assertEqual(self.target.execute("SELECT username_key FROM profiles WHERE user_id='og-d'").fetchone()[0], "testbot")

    def test_second_run_is_idempotent(self):
        rehearsal_import(self.source, self.target)
        before = {t: rows(self.target, t) for t in TABLES}
        rehearsal_import(self.source, self.target)
        self.assertEqual({t: rows(self.target, t) for t in TABLES}, before)

    def test_unknown_account_blocks_import_without_changes(self):
        with self.target:
            self.target.execute("INSERT INTO users VALUES (?,?,?)",
                                ("unknown-person", "other@example.invalid", "fake"))
        before = rows(self.target, "users")
        with self.assertRaisesRegex(RuntimeError, "Unrecognized destination"):
            rehearsal_import(self.source, self.target)
        self.assertEqual(rows(self.target, "users"), before)

    def test_failure_rolls_back_disposable_target(self):
        before = {t: rows(self.target, t) for t in TABLES}
        with self.assertRaisesRegex(RuntimeError, "simulated import failure"):
            rehearsal_import(self.source, self.target, fail_after_table="profiles")
        self.assertEqual({t: rows(self.target, t) for t in TABLES}, before)


if __name__ == "__main__":
    unittest.main()
