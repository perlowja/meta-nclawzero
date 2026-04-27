# Independent pi-gen vs meta-nclawzero structural audit

This Codex audit is retained only for Raspberry Pi and container deliverables.

NVIDIA Jetson family support is deferred pending hardware validation. Previous
references to board-specific recipes, WIC layouts, boot paths, and recovery
logic were removed from this public note.

Still-relevant recommendations:

- Use the boot partition as the last-mile provisioning handoff
- Introduce one documented operator config surface
- Share common image behavior across base, desktop, and agent variants
- Keep partition/export concerns separate from package selection
