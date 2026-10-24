"""Render the known Java-managed MySQL DDL as a reviewable Markdown dictionary.

Run from any directory with Python 3. No database connection or dependencies.
This handles this project's literal DDL and additive migration calls, not arbitrary
Java/SQL. New migration forms must be added here and reviewed against the source.
"""
from pathlib import Path
import re

PROJECT = Path(__file__).resolve().parents[1]
SOURCE = PROJECT / 'src/main/java/com/pos/database'
NAMES = ('DatabaseSetup', 'LedgerSchema', 'CompanionSchema', 'PromotionSchema')
sources = {name: (SOURCE / f'{name}.java').read_text(encoding='utf-8') for name in NAMES}


def split_sql(body):
    """Split declarations without splitting DECIMAL precision or compound keys."""
    start = depth = 0
    quoted = False
    for at, char in enumerate(body):
        if char == "'":
            quoted = not quoted
        if quoted:
            continue
        if char == '(':
            depth += 1
        elif char == ')':
            depth -= 1
        elif char == ',' and depth == 0:
            yield body[start:at].strip()
            start = at + 1
    yield body[start:].strip()


def create_statements(source):
    for match in re.finditer(r'CREATE TABLE IF NOT EXISTS (\w+)\s*\(', source):
        start = match.end()
        depth, quoted = 1, False
        for at in range(start, len(source)):
            char = source[at]
            if char == "'":
                quoted = not quoted
            if quoted:
                continue
            if char == '(':
                depth += 1
            elif char == ')':
                depth -= 1
                if depth == 0:
                    yield match.group(1), source[start:at]
                    break


tables = {}
origins = {}
for owner, source in sources.items():
    for table, body in create_statements(source):
        if table in tables:
            raise ValueError(f'Duplicate table definition: {table}')
        tables[table] = list(split_sql(body))
        origins[table] = owner


def add_column(table, column, definition):
    definition = re.sub(r'\s+AFTER\s+\w+$', '', definition)
    if not any(re.match(rf'{re.escape(column)}\s', entry) for entry in tables[table]):
        tables[table].append(f'{column} {definition}')


for table, column, definition in re.findall(
        r'addColumnIfMissing\(conn,\s*"(\w+)",\s*"(\w+)",\s*"([^"]+)"', sources['DatabaseSetup']):
    add_column(table, column, definition)
for table, column, definition in re.findall(
        r'column\(c,\s*"(\w+)",\s*"(\w+)",\s*"([^"]+)"', sources['LedgerSchema']):
    add_column(table, column, definition)
for column, definition in re.findall(r'column\(c,\s*"(\w+)",\s*"([^"]+)"', sources['PromotionSchema']):
    add_column('Transactions', column, definition)
for table, column, definition in re.findall(
        r'ALTER TABLE (\w+) ADD COLUMN (\w+) ([^"]+)', sources['CompanionSchema']):
    add_column(table, column, definition)

# Mirror the final migration semantics, rather than documenting obsolete base DDL.
for table, column, definition in re.findall(
        r'ALTER TABLE (\w+) MODIFY (\w+) ([^"]+)', sources['LedgerSchema']):
    tables[table] = [f'{column} {definition}' if re.match(rf'{column}\s', item) else item
                     for item in tables[table]]
restriction = re.search(r"k.TABLE_NAME IN \(([^)]+)\)", sources['LedgerSchema']).group(1)
for table in re.findall(r"'(\w+)'", restriction):
    tables[table] = [item.replace('ON DELETE CASCADE', 'ON DELETE RESTRICT') for item in tables[table]]

lines = ['# Desktop database table reference', '',
         'Generated from the literal Java DDL and known additive migrations. Read with the '
         '[database guide](database.md), which explains meaning, transactions and logical relationships.', '',
         'Regenerate with `python tools/generate_database_reference.py` from `pos-desktop`. '
         'No database is opened. This is the expected current-source schema, not an inspection of an installed database. '
         'Column order is not significant. Future migration forms need corresponding generator support.', '',
         'Definitions below include known column additions, the supervisor hash-width change and the '
         'ledger cascade-to-RESTRICT migration. Generated constraint names and implicit MySQL indexes '
         'are omitted; use `SHOW CREATE TABLE` on a deployed database for those details.', '',
         f'Tables: **{len(tables)}**.', '']
for table, declarations in tables.items():
    lines += [f'## {table}', '',
              f'Base owner: [`{origins[table]}`](../src/main/java/com/pos/database/{origins[table]}.java).', '',
              '| Column | SQL definition |', '| --- | --- |']
    constraints = []
    for declaration in declarations:
        declaration = re.sub(r'\s+', ' ', declaration)
        if re.match(r'(?:PRIMARY|FOREIGN|INDEX|UNIQUE|CONSTRAINT)\b', declaration):
            constraints.append(declaration)
        else:
            column, definition = declaration.split(' ', 1)
            lines.append(f'| `{column}` | `{definition}` |')
    if constraints:
        lines += ['', 'Declared table constraints/indexes:', '']
        lines += [f'- `{constraint}`' for constraint in constraints]
    lines.append('')
destination = PROJECT / 'docs/database-tables.md'
destination.write_text('\n'.join(lines), encoding='utf-8')
print(f'Generated {len(tables)} table definitions in {destination}')
