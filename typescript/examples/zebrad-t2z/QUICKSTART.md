# Quick Start - Containerized Examples

Everything runs inside Docker - no local Node.js or platform compatibility issues!

## 🚀 Start Everything

```bash
# Start Zebra + Examples container
npm run docker:up

# Wait ~30 seconds for Zebra to be ready
# Check logs:
npm run docker:logs:zebra
```

## 📝 Run Examples

All examples run inside the Docker container (linux/amd64):

```bash
# Run setup (creates test addresses, mines blocks)
npm run setup

# Run individual examples
npm run example:1  # Single output
npm run example:2  # Multiple outputs
npm run example:3  # Mixed outputs
npm run example:4  # Attack scenario

# Or run all at once
npm run all
```

## 🔧 Development

```bash
# Get a shell inside the examples container
npm run docker:shell

# Then run commands directly:
tsx src/examples/1-single-output.ts

# View logs
npm run docker:logs:zebra
npm run docker:logs:examples
```

## 🧹 Cleanup

```bash
# Stop and remove everything
npm run docker:down
```

## 🎯 Architecture

```
┌─────────────────────────────────────┐
│  Host Machine (any platform)       │
│                                     │
│  ┌─────────────────────────────┐  │
│  │  Docker (linux/amd64)        │  │
│  │                               │  │
│  │  ┌───────────┐  ┌──────────┐│  │
│  │  │  Zebra    │  │ Examples ││  │
│  │  │  (node)   │←→│ (Node.js)││  │
│  │  └───────────┘  └──────────┘│  │
│  │       ↓              ↓       │  │
│  │   regtest      @mayaprotocol│  │
│  │                 /zcash-ts    │  │
│  └─────────────────────────────┘  │
└─────────────────────────────────────┘
```

## ✅ Benefits

- **No platform issues**: Everything runs in linux/amd64
- **Isolated**: No local Node.js pollution
- **Reproducible**: Same environment for everyone
- **Easy**: Just `npm run docker:up`

## 📚 Next Steps

After running the examples, check out:
- `src/examples/` - Example code
- `ZEBRA-NOTES.md` - Zebra vs zcashd differences
- `README.md` - Full documentation
