extends CharacterBody3D
## Nóctis - controlador de prova técnica de movimento em 3D.
## A cápsula 3D é temporária; será substituída pelo modelo definitivo com rig.
const SPEED: float = 5.2
const JUMP_SPEED: float = 4.4
var touch_id: int = -1
var start_pos: Vector2 = Vector2.ZERO
var touch_vector: Vector2 = Vector2.ZERO
var gravity_force: float = 18.0

func _ready() -> void:
    _create_provisional_body()
    _create_camera()
    _create_collision()

func _create_provisional_body() -> void:
    var visual := MeshInstance3D.new()
    visual.name = "MODELO_TEMPORARIO"
    var capsule := CapsuleMesh.new()
    capsule.radius = 0.37
    capsule.height = 1.9
    visual.mesh = capsule
    var material := StandardMaterial3D.new()
    material.albedo_color = Color(0.18, 0.13, 0.32)
    material.metallic = 0.25
    visual.material_override = material
    add_child(visual)

func _create_collision() -> void:
    var col := CollisionShape3D.new()
    var shape := CapsuleShape3D.new()
    shape.radius = 0.37
    shape.height = 1.9
    col.shape = shape
    add_child(col)

func _create_camera() -> void:
    var camera := Camera3D.new()
    camera.name = "CameraTerceiraPessoa"
    camera.position = Vector3(0, 2.6, 6.5)
    camera.rotation_degrees.x = -12
    camera.current = true
    add_child(camera)

func _unhandled_input(event: InputEvent) -> void:
    if event is InputEventScreenTouch:
        if event.pressed and event.position.x < get_viewport().get_visible_rect().size.x * 0.5 and touch_id == -1:
            touch_id = event.index
            start_pos = event.position
        elif not event.pressed and event.index == touch_id:
            touch_id = -1
            touch_vector = Vector2.ZERO
    elif event is InputEventScreenDrag and event.index == touch_id:
        touch_vector = ((event.position - start_pos) / 85.0).limit_length(1.0)

func _physics_process(delta: float) -> void:
    var input_vec := touch_vector
    if Input.is_key_pressed(KEY_A) or Input.is_key_pressed(KEY_LEFT):
        input_vec.x -= 1.0
    if Input.is_key_pressed(KEY_D) or Input.is_key_pressed(KEY_RIGHT):
        input_vec.x += 1.0
    if Input.is_key_pressed(KEY_W) or Input.is_key_pressed(KEY_UP):
        input_vec.y -= 1.0
    if Input.is_key_pressed(KEY_S) or Input.is_key_pressed(KEY_DOWN):
        input_vec.y += 1.0
    input_vec = input_vec.limit_length(1.0)
    velocity.x = input_vec.x * SPEED
    velocity.z = input_vec.y * SPEED
    if not is_on_floor():
        velocity.y -= gravity_force * delta
    elif Input.is_key_pressed(KEY_SPACE):
        velocity.y = JUMP_SPEED
    else:
        velocity.y = -0.05
    move_and_slide()
