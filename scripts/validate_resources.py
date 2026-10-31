#!/usr/bin/env python3
"""Validate EA3's model parents and inherited texture references against local dependencies."""
import json
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'src/main/resources'
archives = [ZipFile(path) for path in (ROOT / 'libs').glob('*.jar')]
archives += [ZipFile(path) for path in (ROOT / 'build/moddev/artifacts').glob('*minecraft-resources.jar')]


def read(path):
    local = RESOURCES / path
    if local.exists():
        return local.read_bytes()
    for archive in archives:
        if path in archive.namelist():
            return archive.read(path)
    raise AssertionError(f'Missing resource: {path}')


def model_data(model, chain=()):
    assert model not in chain, f'Cyclic model parent: {chain} -> {model}'
    namespace, path = model.split(':') if ':' in model else ('minecraft', model)
    data = json.loads(read(f'assets/{namespace}/models/{path}.json'))
    textures = {}
    if 'parent' in data and not data['parent'].startswith('builtin/'):
        textures.update(model_data(data['parent'], (*chain, model)))
    textures.update(data.get('textures', {}))
    return textures


try:
    for path in RESOURCES.rglob('*.json'):
        json.loads(path.read_text())
    assets = RESOURCES / 'assets/electricadvantage'
    for path in (assets / 'blockstates').glob('*.json'):
        data = json.loads(path.read_text())
        for variants in data.get('variants', {}).values():
            for variant in variants if isinstance(variants, list) else [variants]:
                model_data(variant['model'])
    models = list((assets / 'models').rglob('*.json'))
    for path in models:
        model = 'electricadvantage:' + str(path.relative_to(assets / 'models').with_suffix(''))
        textures = model_data(model)
        for key, texture in textures.items():
            seen = set()
            while texture.startswith('#'):
                assert texture not in seen, f'Cyclic texture: {model}/{key}'
                seen.add(texture)
                texture = textures[texture[1:]]
            namespace, value = texture.split(':') if ':' in texture else ('minecraft', texture)
            assert value.startswith(('block/', 'item/')), f'Non-atlas texture path: {texture}'
            read(f'assets/{namespace}/textures/{value}.png')
    print(f'Validated JSON, blockstate model references, {len(models)} models and their inherited textures.')
finally:
    for archive in archives:
        archive.close()
