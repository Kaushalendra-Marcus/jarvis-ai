# Hourly backend backup when logged in (later)

Idea we agreed to park for now. When the user is logged in, back up chats and tasks to Postgres about once an hour, then build Mem0 from that.

This is nice for multi device use, but it adds auth, sync, and privacy work. Risky before submission, so keep it for after the assessment.
