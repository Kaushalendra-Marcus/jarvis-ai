# Finish Mem0 delete and recent handling

Delete currently only clears Room, so cloud copies stick around. And recent from Mem0 always comes back empty.

What we want: real delete on Mem0, real recent fetch, and show errors instead of failing silently.

This keeps view and delete honest for both local and cloud memory.
