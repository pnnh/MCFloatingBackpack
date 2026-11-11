#!/usr/bin/env python3
"""转储玩家数据（playerdata/*.dat）里每只浮动背包的修订、编号和嵌套的物品 id，用来对比"被拒后背包零变化"一类的结论。

用法：python3 -I scripts/ai-gui/bp_dump.py 文件.dat [文件2.dat …]
取存档：docker cp "mcfb-gui:/work/run/saves/New World/playerdata/." 目录/
注意：玩家数据只在保存时写出，要先在游戏里 Save and Quit。
"""
import gzip
import struct
import sys


def read(data):
    pos = [0]

    def u(fmt, n):
        v = struct.unpack_from(fmt, data, pos[0])
        pos[0] += n
        return v[0]

    def string():
        n = u('>H', 2)
        s = data[pos[0]:pos[0] + n].decode('utf-8', 'replace')
        pos[0] += n
        return s

    def payload(t):
        if t == 1: return u('>b', 1)
        if t == 2: return u('>h', 2)
        if t == 3: return u('>i', 4)
        if t == 4: return u('>q', 8)
        if t == 5: return u('>f', 4)
        if t == 6: return u('>d', 8)
        if t == 7:
            n = u('>i', 4); pos[0] += n; return '<bytes %d>' % n
        if t == 8: return string()
        if t == 9:
            it = u('>b', 1); n = u('>i', 4)
            return [payload(it) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                tt = u('>b', 1)
                if tt == 0: break
                k = string(); d[k] = payload(tt)
            return d
        if t == 11:
            n = u('>i', 4); pos[0] += 4 * n; return '<ints %d>' % n
        if t == 12:
            n = u('>i', 4); pos[0] += 8 * n; return '<longs %d>' % n
        raise ValueError('bad tag %d' % t)

    t = u('>b', 1)
    string()
    return payload(t)


def load(path):
    return read(gzip.open(path).read())


def find(node, key_sub, out):
    if isinstance(node, dict):
        for k, v in node.items():
            if key_sub in k.lower():
                out.append((k, v))
            find(v, key_sub, out)
    elif isinstance(node, list):
        for v in node:
            find(v, key_sub, out)


def ids(node, out):
    if isinstance(node, dict):
        if isinstance(node.get('id'), str):
            out.append(node['id'])
        for v in node.values():
            ids(v, out)
    elif isinstance(node, list):
        for v in node:
            ids(v, out)


def backpacks(path):
    """返回 [(槽位, 物品 id, 背包内容 dict)]，供比较。"""
    d = load(path)
    out = []
    for it in d.get('Inventory', []):
        if 'floating_backpack' in str(it.get('id', '')):
            out.append((it.get('Slot'), it['id'], it.get('components', {}).get('mcfloatingbackpack:backpack_contents', {})))
    return out


def main(paths):
    for f in paths:
        print('FILE', f.split('/')[-1])
        for slot, item_id, contents in backpacks(f):
            revs = []; find(contents, 'revision', revs)
            nested = []; ids(contents, nested)
            print(' slot', slot, item_id,
                  'revision:', [(k, v) for k, v in revs] or '（没有 data_revision 字段）',
                  'nested:', sorted(set(nested)))


if __name__ == '__main__':
    main(sys.argv[1:])
