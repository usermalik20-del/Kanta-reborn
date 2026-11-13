#!/usr/bin/env python3
"""Validate 64-bit PT_LOAD alignment inside the actual APK (or an upstream AAR)."""
import argparse
from pathlib import Path
import struct
import sys
import zipfile

PAGE = 16384
ABIS = {'arm64-v8a': 183, 'x86_64': 62}

def verify_elf(data, machine):
    if len(data) < 64 or data[:6] != b'\x7fELF\x02\x01':
        raise ValueError('Expected little-endian ELF64')
    if struct.unpack_from('<H', data, 18)[0] != machine:
        raise ValueError('ELF machine does not match ABI')
    offset = struct.unpack_from('<Q', data, 32)[0]
    size, count = struct.unpack_from('<HH', data, 54)
    if size < 56 or not count or offset + size * count > len(data):
        raise ValueError('Invalid program header table')
    loads = 0
    for index in range(count):
        kind, flags, file_offset, address, physical, file_size, mem_size, align = struct.unpack_from('<IIQQQQQQ', data, offset + size * index)
        if kind != 1:
            continue
        loads += 1
        if align < PAGE or align & (align - 1) or (address - file_offset) % PAGE:
            raise ValueError(f'PT_LOAD is not 16 KB compatible: align={align}')
    if not loads:
        raise ValueError('No PT_LOAD segments')

def verify_archive(path):
    seen = set()
    libraries = 0
    with zipfile.ZipFile(path) as archive:
        for entry in archive.infolist():
            parts = entry.filename.split('/')
            if len(parts) != 3 or parts[0] not in ('jni', 'lib') or not parts[2].endswith('.so') or parts[1] not in ABIS:
                continue
            try:
                # Uncompressed APK libraries are mmap'd from the ZIP itself; the
                # data offset must also be page-aligned, independently of ELF.
                if parts[0] == 'lib' and entry.compress_type == zipfile.ZIP_STORED:
                    with path.open('rb') as file:
                        file.seek(entry.header_offset)
                        header = file.read(30)
                    if len(header) != 30 or header[:4] != b'PK\x03\x04':
                        raise ValueError('Invalid ZIP local header')
                    filename_length, extra_length = struct.unpack_from('<HH', header, 26)
                    data_offset = entry.header_offset + 30 + filename_length + extra_length
                    if data_offset % PAGE:
                        raise ValueError('Uncompressed APK library is not 16 KB ZIP-aligned')
                verify_elf(archive.read(entry), ABIS[parts[1]])
            except (ValueError, struct.error) as error:
                raise ValueError(f'{entry.filename}: {error}') from error
            seen.add(parts[1])
            libraries += 1
    if seen != set(ABIS):
        raise ValueError(f'Missing required 64-bit ABI libraries: {set(ABIS) - seen}')
    print(f'{path}: {libraries} 64-bit native libraries have 16 KB ELF alignment')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archives', nargs='+', type=Path)
    args = parser.parse_args()
    try:
        for archive in args.archives:
            verify_archive(archive)
        return 0
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        print(error, file=sys.stderr)
        return 1

if __name__ == '__main__':
    sys.exit(main())
