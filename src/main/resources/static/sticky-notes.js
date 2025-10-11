
async function init() {
    const response = await fetch("/memo/all", {
        method: "GET"
    });

    const allMemos = await response.json();

    allMemos.forEach(memo => {
        createStickyNote(memo);
    });
}

// Function to create and display a sticky note
function createStickyNote(noteData) {
    const noteDiv = document.createElement("div");
    noteDiv.classList.add("sticky-note");

    // Set position from data
    noteDiv.style.left = noteData.x + "px";
    noteDiv.style.top = noteData.y + "px";

    const title = document.createElement("h4");
    title.innerText = noteData.title;
    const content = document.createElement("p");
    content.innerText = noteData.content;

    noteDiv.appendChild(title);
    noteDiv.appendChild(content);
    document.body.appendChild(noteDiv);

    makeNoteDraggable(noteDiv, noteData);
}

// Function to make a note draggable (same as before)
function makeNoteDraggable(noteDiv, noteData) {
    noteDiv.onmousedown = function(event) {
        let shiftX = event.clientX - noteDiv.getBoundingClientRect().left;
        let shiftY = event.clientY - noteDiv.getBoundingClientRect().top;

        function moveAt(pageX, pageY) {
            noteDiv.style.left = pageX - shiftX + "px";
            noteDiv.style.top = pageY - shiftY + "px";
        }

        function onMouseMove(event) {
            moveAt(event.pageX, event.pageY);
        }

        document.addEventListener("mousemove", onMouseMove);

        noteDiv.onmouseup = function() {
            document.removeEventListener("mousemove", onMouseMove);
            noteDiv.onmouseup = null;

            noteData.x = parseInt(noteDiv.style.left);
            noteData.y = parseInt(noteDiv.style.top);
            updateCoordinates(noteData);
        };
    };

    noteDiv.ondragstart = function() {
        return false;
    };
}

async function updateCoordinates(noteData) {
    await fetch("/memo/coordinates", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(noteData)
    });
}

window.onload = init;

const newNoteButton = document.getElementById("newNoteButton");

// Event listener for the button
newNoteButton.addEventListener("click", async () => {
    const newMemo = {
        title: "My new note",
        content: "This is a sticky note!",
        x: 50,
        y: 50
    };

    const response = await fetch("/memo/", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(newMemo)
    });

    const savedMemo = await response.json();
    console.log(savedMemo);

    createStickyNote(savedMemo);
});
