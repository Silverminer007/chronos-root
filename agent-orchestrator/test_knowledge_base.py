import json
import tempfile
from datetime import datetime
from pathlib import Path
import pytest

from knowledge_base import KnowledgeBaseManager, KnowledgeBase


class TestKnowledgeBaseManager:
    """Test KB persistence with atomic writes."""

    def test_load_creates_default_kb_if_missing(self):
        """Loading a missing KB file returns default structure."""
        with tempfile.TemporaryDirectory() as tmpdir:
            kb_file = Path(tmpdir) / "kb.json"
            manager = KnowledgeBaseManager(str(kb_file))

            kb = manager.load()

            assert kb.tickets == {}
            assert kb.agents == {}
            assert kb.outcomes == []

    def test_load_existing_kb_file(self):
        """Loading an existing KB file deserializes it correctly."""
        with tempfile.TemporaryDirectory() as tmpdir:
            kb_file = Path(tmpdir) / "kb.json"
            initial_data = {
                "tickets": {"123": {"id": 123, "title": "Test"}},
                "agents": {"tdd": {"type": "tdd", "success_rate": 0.95}},
                "outcomes": []
            }
            kb_file.write_text(json.dumps(initial_data))

            manager = KnowledgeBaseManager(str(kb_file))
            kb = manager.load()

            assert kb.tickets["123"]["id"] == 123
            assert kb.agents["tdd"]["type"] == "tdd"

    def test_save_performs_atomic_write(self):
        """Saving KB uses atomic rename to prevent partial writes."""
        with tempfile.TemporaryDirectory() as tmpdir:
            kb_file = Path(tmpdir) / "kb.json"
            manager = KnowledgeBaseManager(str(kb_file))

            kb = KnowledgeBase()
            kb.add_ticket(123, "Test Ticket", "test body")

            manager.save(kb)

            assert kb_file.exists()
            loaded = json.loads(kb_file.read_text())
            assert loaded["tickets"]["123"]["title"] == "Test Ticket"

    def test_save_handles_corrupted_file(self):
        """Saving to corrupted file doesn't lose data (atomic rename)."""
        with tempfile.TemporaryDirectory() as tmpdir:
            kb_file = Path(tmpdir) / "kb.json"
            kb_file.write_text("corrupt")

            manager = KnowledgeBaseManager(str(kb_file))
            kb = KnowledgeBase()
            kb.add_ticket(123, "Title", "body")

            manager.save(kb)

            loaded = json.loads(kb_file.read_text())
            assert loaded["tickets"]["123"]["title"] == "Title"


class TestKnowledgeBase:
    """Test KB data structure and operations."""

    def test_add_ticket_stores_metadata(self):
        """Adding a ticket stores its metadata."""
        kb = KnowledgeBase()

        kb.add_ticket(123, "Test Ticket", "Test description", labels=["ready-for-agent"])

        assert kb.tickets["123"]["id"] == 123
        assert kb.tickets["123"]["title"] == "Test Ticket"
        assert kb.tickets["123"]["description"] == "Test description"
        assert kb.tickets["123"]["labels"] == ["ready-for-agent"]

    def test_add_agent_stores_capabilities(self):
        """Adding an agent stores its type and capabilities."""
        kb = KnowledgeBase()

        kb.add_agent("tdd", ["python", "testing"], success_rate=0.95)

        assert kb.agents["tdd"]["type"] == "tdd"
        assert kb.agents["tdd"]["capabilities"] == ["python", "testing"]
        assert kb.agents["tdd"]["success_rate"] == 0.95

    def test_record_outcome_tracks_agent_ticket_relationship(self):
        """Recording an outcome tracks which agent worked on which ticket."""
        kb = KnowledgeBase()
        kb.add_ticket(123, "Test", "desc")
        kb.add_agent("tdd", ["python"])

        kb.record_outcome(
            ticket_id=123,
            agent_type="tdd",
            status="success",
            pr_number=42
        )

        assert len(kb.outcomes) == 1
        outcome = kb.outcomes[0]
        assert outcome["ticket_id"] == 123
        assert outcome["agent_type"] == "tdd"
        assert outcome["status"] == "success"
        assert outcome["pr_number"] == 42
        assert "timestamp" in outcome

    def test_query_tickets_by_label(self):
        """Query tickets by label returns matching tickets."""
        kb = KnowledgeBase()
        kb.add_ticket(123, "Ready 1", "desc", labels=["ready-for-agent"])
        kb.add_ticket(124, "Ready 2", "desc", labels=["ready-for-agent"])
        kb.add_ticket(125, "Not Ready", "desc", labels=["needs-info"])

        result = kb.query_tickets_by_label("ready-for-agent")

        assert len(result) == 2
        assert all(t["id"] in [123, 124] for t in result)

    def test_get_agent_stats(self):
        """Getting agent stats computes success rate from outcomes."""
        kb = KnowledgeBase()
        kb.add_agent("tdd", ["python"])
        kb.record_outcome(123, "tdd", "success")
        kb.record_outcome(124, "tdd", "success")
        kb.record_outcome(125, "tdd", "failed")

        stats = kb.get_agent_stats("tdd")

        assert stats["total_attempts"] == 3
        assert stats["success_count"] == 2
        assert stats["success_rate"] == pytest.approx(0.67, abs=0.01)

    def test_kb_serializes_to_dict(self):
        """KB can be serialized to dict for JSON storage."""
        kb = KnowledgeBase()
        kb.add_ticket(123, "Test", "desc")
        kb.add_agent("tdd", ["python"])
        kb.record_outcome(123, "tdd", "success", pr_number=42)

        data = kb.to_dict()

        assert isinstance(data, dict)
        assert "tickets" in data
        assert "agents" in data
        assert "outcomes" in data
        assert data["tickets"]["123"]["id"] == 123

    def test_kb_deserializes_from_dict(self):
        """KB can be deserialized from dict."""
        data = {
            "tickets": {"123": {"id": 123, "title": "Test", "description": "desc", "labels": []}},
            "agents": {"tdd": {"type": "tdd", "capabilities": ["python"], "success_rate": 0.95}},
            "outcomes": [{"ticket_id": 123, "agent_type": "tdd", "status": "success", "timestamp": "2026-09-27T12:00:00Z"}]
        }

        kb = KnowledgeBase.from_dict(data)

        assert kb.tickets["123"]["title"] == "Test"
        assert kb.agents["tdd"]["type"] == "tdd"
        assert len(kb.outcomes) == 1


class TestAgentQuerying:
    """Test agent discovery and capability matching."""

    def test_query_agents_by_capability(self):
        """Find agents with specific capabilities."""
        kb = KnowledgeBase()
        kb.add_agent("tdd", ["python", "testing"])
        kb.add_agent("validator", ["java", "testing"])
        kb.add_agent("frontend", ["typescript", "react"])

        result = kb.query_agents_by_capability("testing")

        assert len(result) == 2
        assert all(a["type"] in ["tdd", "validator"] for a in result)

    def test_find_best_agent_for_ticket(self):
        """Find agent with highest success rate for a ticket's capabilities."""
        kb = KnowledgeBase()
        kb.add_ticket(100, "Python Testing", "needs python testing", labels=["python", "testing"])
        kb.add_agent("tdd", ["python", "testing"], success_rate=0.95)
        kb.add_agent("validator", ["python"], success_rate=0.8)

        best = kb.find_best_agent_for_capabilities(["python", "testing"])

        assert best["type"] == "tdd"
        assert best["success_rate"] == 0.95

    def test_find_best_agent_returns_none_if_no_match(self):
        """Returns None if no agent has matching capabilities."""
        kb = KnowledgeBase()
        kb.add_agent("tdd", ["python"])

        result = kb.find_best_agent_for_capabilities(["rust", "webassembly"])

        assert result is None


class TestDependencies:
    """Test ticket dependency tracking."""

    def test_add_dependency_links_tickets(self):
        """Adding a dependency links two tickets."""
        kb = KnowledgeBase()
        kb.add_ticket(100, "Parent", "desc")
        kb.add_ticket(101, "Child", "desc")

        kb.add_dependency(101, 100)  # 101 depends on 100

        assert kb.get_ticket_dependencies(101) == [100]

    def test_query_blocking_tickets(self):
        """Find all tickets blocked by a given ticket."""
        kb = KnowledgeBase()
        kb.add_ticket(100, "Base", "desc")
        kb.add_ticket(101, "Blocked 1", "desc")
        kb.add_ticket(102, "Blocked 2", "desc")
        kb.add_ticket(103, "Independent", "desc")

        kb.add_dependency(101, 100)
        kb.add_dependency(102, 100)

        result = kb.get_blocking_tickets(100)

        assert sorted(result) == [101, 102]

    def test_get_dependency_chain(self):
        """Get full dependency chain for a ticket."""
        kb = KnowledgeBase()
        kb.add_ticket(100, "A", "desc")
        kb.add_ticket(101, "B", "desc")
        kb.add_ticket(102, "C", "desc")

        kb.add_dependency(101, 100)
        kb.add_dependency(102, 101)

        chain = kb.get_dependency_chain(102)

        assert chain == [102, 101, 100]


class TestConfiguration:
    """Test KB configuration storage."""

    def test_set_and_get_config(self):
        """Store and retrieve configuration values."""
        kb = KnowledgeBase()

        kb.set_config("max_concurrent_agents", 5)
        kb.set_config("poll_interval", 300)

        assert kb.get_config("max_concurrent_agents") == 5
        assert kb.get_config("poll_interval") == 300

    def test_get_missing_config_with_default(self):
        """Getting missing config returns default value."""
        kb = KnowledgeBase()

        result = kb.get_config("nonexistent", default=42)

        assert result == 42

    def test_config_persists_in_serialization(self):
        """Configuration is preserved when serializing/deserializing."""
        kb = KnowledgeBase()
        kb.set_config("setting", "value")

        data = kb.to_dict()
        kb2 = KnowledgeBase.from_dict(data)

        assert kb2.get_config("setting") == "value"
