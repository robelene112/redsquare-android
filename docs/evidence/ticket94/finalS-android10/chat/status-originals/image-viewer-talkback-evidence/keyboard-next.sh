exec 2>&1
trap 'sendevent /dev/input/event12 1 106 0; sendevent /dev/input/event12 1 125 0; sendevent /dev/input/event12 0 0 0' EXIT
sendevent /dev/input/event12 1 125 1 && sendevent /dev/input/event12 0 0 0 && sendevent /dev/input/event12 1 106 1 && sendevent /dev/input/event12 0 0 0 && sendevent /dev/input/event12 1 106 0 && sendevent /dev/input/event12 0 0 0 && sendevent /dev/input/event12 1 125 0 && sendevent /dev/input/event12 0 0 0 && echo sent
