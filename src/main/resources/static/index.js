const newNoteButton = document.getElementById("newNoteButton");

// Function to create and display a sticky note
function createStickyNote(noteData) {
    const noteDiv = document.createElement("div");
    noteDiv.classList.add("sticky-note");
    noteDiv.style.position = "absolute";
    noteDiv.style.left = noteData.x + "px";
    noteDiv.style.top = noteData.y + "px";
    noteDiv.style.width = "200px";
    noteDiv.style.height = "150px";
    noteDiv.style.backgroundColor = "#fffb88";
    noteDiv.style.border = "1px solid #e0e0e0";
    noteDiv.style.padding = "10px";
    noteDiv.style.boxShadow = "2px 2px 5px rgba(0,0,0,0.3)";
    noteDiv.style.cursor = "move";

    const title = document.createElement("h4");
    title.innerText = noteData.title;
    const content = document.createElement("p");
    content.innerText = noteData.content;

    noteDiv.appendChild(title);
    noteDiv.appendChild(content);
    document.body.appendChild(noteDiv);

    makeNoteDraggable(noteDiv);
}

// Function to make a note draggable
function makeNoteDraggable(noteDiv) {
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
        };
    };

    noteDiv.ondragstart = function() {
        return false;
    };
}

// Event listener for the button
newNoteButton.addEventListener("click", async () => {
    const newMemo = {
        title: "My new note",
        content: "This is a sticky note!",
        x: 50,
        y: 50
    };

    await fetch("/memo/", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(newMemo)
    });

    createStickyNote(newMemo);
});
