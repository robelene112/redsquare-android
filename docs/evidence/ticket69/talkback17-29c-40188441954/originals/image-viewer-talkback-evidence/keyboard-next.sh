exec 2>&1
trap 'sendevent /dev/input/event1 1 106 0; sendevent /dev/input/event1 1 56 0; sendevent /dev/input/event1 0 0 0' EXIT
sendevent /dev/input/event1 1 56 1 && sendevent /dev/input/event1 0 0 0 && sendevent /dev/input/event1 1 106 1 && sendevent /dev/input/event1 0 0 0 && sendevent /dev/input/event1 1 106 0 && sendevent /dev/input/event1 0 0 0 && sendevent /dev/input/event1 1 56 0 && sendevent /dev/input/event1 0 0 0 && echo sent
