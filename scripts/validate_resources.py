#!/usr/bin/env python3
"""Validate EA3's model parents and inherited texture references against its dependencies.

Dependency JARs come from Gradle's cache, at the versions in gradle.properties, so run a
Gradle build (which downloads them) first.
"""
import json
import re
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'src/main/resources'
GRADLE_CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
# (Maven group, artifact, gradle.properties version key)
DEPENDENCIES = [
    ('zone.moddev.mc.lib', 'mmdlib', 'mmdlib_version'),
    ('zone.moddev.mc.poweradvantage', 'PowerAdvantage', 'poweradvantage_version'),
    ('zone.moddev.mc.orespawn', 'OreSpawn', 'orespawn_version'),
]


def gradle_properties():
    properties = {}
    for line in (ROOT / 'gradle.properties').read_text().splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            properties[key.strip()] = value.strip()
    return properties


def dependency_jars():
    properties = gradle_properties()
    for group, artifact, key in DEPENDENCIES:
        version = properties[key]
        jars = list((GRADLE_CACHE / group / artifact / version).glob(f'*/{artifact}-{version}.jar'))
        assert jars, f'{artifact} {version} is not in the Gradle cache; run ./gradlew build first'
        yield jars[0]


archives = [ZipFile(path) for path in dependency_jars()]
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
    # XML machines have generated blockstates; check the models their appearances name.
    machines = list((RESOURCES / 'mmdlib/machine').rglob('*.xml'))
    for path in machines:
        for model in re.findall(r'\bmodel="([^"]+)"', path.read_text()):
            model_data(model)
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
    print(f'Validated JSON, blockstate and {len(machines)} XML machine model references, '
          f'{len(models)} models and their inherited textures.')
finally:
    for archive in archives:
        archive.close()
