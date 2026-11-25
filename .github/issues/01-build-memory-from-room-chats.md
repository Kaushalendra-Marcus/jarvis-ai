# Build memory from Room chats on a schedule

Right now Mem0 only saves when the agent calls it during a chat.

What we want: a small hourly and daily worker that reads recent Room chats and saves the useful bits to memory. No Postgres needed for this.

Memory tab should just pick it up automatically since it already reads Room and Mem0.

Keep it offline safe. If there is no Mem0 key, Room stays the source of truth.
