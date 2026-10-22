#!/usr/bin/env python3
"""Compatibility entry point for the bounded OnlyHaven category contract probe."""
from audit_source_contracts import main

if __name__ == '__main__':
    import sys
    sys.argv.append('--onlyhaven')
    main()
