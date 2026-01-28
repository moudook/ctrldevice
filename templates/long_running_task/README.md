# Long Running Task Template

**Copy this folder to `agent-core/src/tasks/[task_name]`**

## Overview
This template is for tasks that might take hours or days, requiring robust state management.

## Files
1.  `workflow.py`: The main state machine logic.
2.  `state_schema.json`: Defines the variables to be saved.
3.  `recovery.md`: Instructions on how to handle failures.

## Workflow Pattern (Python)
```python
class EmailForwardingTask:
    def __init__(self, state_manager):
        self.state_manager = state_manager
        self.task_id = "email_internship_001"
        self.state = self.state_manager.load_state(self.task_id) or {
            "status": "INIT",
            "retries": 0
        }

    def run(self):
        while self.state["status"] != "DONE":
            if self.state["status"] == "INIT":
                self.open_gmail()
                self.state["status"] = "SEARCHING"
                self.save()
            
            elif self.state["status"] == "SEARCHING":
                found = self.find_email()
                if found:
                    self.state["status"] = "FORWARDING"
                else:
                    self.state["retries"] += 1
                    if self.state["retries"] > 3:
                        # Sleep logic here
                        return "WAITING"
                self.save()

    def save(self):
        self.state_manager.save_state(self.task_id, self.state)
```
