extends Node3D
## PROVA TÉCNICA: geometria provisória para testar movimento, câmera e colisão.
## NÃO é o visual final de Nóctis, do cenário ou dos monstros.

const PLAYER_SCRIPT = preload("res://scripts/noctis.gd")

func _ready() -> void:
    _setup_environment()
    _create_ground()
    _create_player()
    _create_marker(Vector3(-7, 1.4, -5), Color(0.4, 0.7, 0.95))
    _create_marker(Vector3(6, 1.4, -8), Color(0.7, 0.3, 0.85))
    _setup_hud()

func _setup_environment() -> void:
    var environment_node := WorldEnvironment.new()
    var env := Environment.new()
    env.background_mode = Environment.BG_COLOR
    env.background_color = Color(0.11, 0.16, 0.25)
    env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    env.ambient_light_color = Color(0.56, 0.64, 0.82)
    env.ambient_light_energy = 0.7
    environment_node.environment = env
    add_child(environment_node)
    var sun := DirectionalLight3D.new()
    sun.rotation_degrees = Vector3(-54, 28, 5)
    sun.light_energy = 1.1
    sun.shadow_enabled = true
    add_child(sun)

func _create_ground() -> void:
    var ground := StaticBody3D.new()
    ground.name = "Solo_Provisorio"
    var surface := MeshInstance3D.new()
    var mesh := BoxMesh.new()
    mesh.size = Vector3(70, 1.0, 70)
    surface.mesh = mesh
    var mat := StandardMaterial3D.new()
    mat.albedo_color = Color(0.16, 0.28, 0.23)
    mat.roughness = 0.93
    surface.material_override = mat
    ground.add_child(surface)
    var collider := CollisionShape3D.new()
    var shape := BoxShape3D.new()
    shape.size = mesh.size
    collider.shape = shape
    ground.add_child(collider)
    ground.position.y = -0.5
    add_child(ground)

func _create_player() -> void:
    var player := CharacterBody3D.new()
    player.name = "Noctis_Provisorio"
    player.position = Vector3(0, 1.5, 0)
    player.set_script(PLAYER_SCRIPT)
    add_child(player)

func _create_marker(position_3d: Vector3, tone: Color) -> void:
    var marker := MeshInstance3D.new()
    var mesh := SphereMesh.new()
    mesh.radius = 0.48
    mesh.height = 0.96
    marker.mesh = mesh
    var material := StandardMaterial3D.new()
    material.albedo_color = tone
    material.emission_enabled = true
    material.emission = tone
    marker.material_override = material
    marker.position = position_3d
    add_child(marker)

func _setup_hud() -> void:
    var canvas := CanvasLayer.new()
    add_child(canvas)
    var panel := PanelContainer.new()
    panel.position = Vector2(25, 25)
    var label := Label.new()
    label.text = "AETHERIA · PROTÓTIPO TÉCNICO\nNÓCTIS · Sombra\nWASD / setas · ou toque e arraste à esquerda\nA geometria é TEMPORÁRIA. Não é arte final."
    panel.add_child(label)
    canvas.add_child(panel)
