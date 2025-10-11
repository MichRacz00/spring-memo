const newNoteButton = document.getElementById("newNoteButton");

newNoteButton.addEventListener("click", async () => {
    const newMemo = {
        title: "My new note",
        content: "This is a sticky note!",
        x: 0,
        y: 0
    };

    await fetch("/memo/", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(newMemo)
    });
});

