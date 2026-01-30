
let currentNoteType = 'StickyNote';

const chooseStickyNoteButton = document.getElementById('chooseStickyNote');
const chooseNotepadButton = document.getElementById('chooseNotepad');
//const choosePlaceholderButton = document.getElementById('choosePlaceholder');

const noteTitleInput = document.getElementById('noteTitle');
const noteContentInput = document.getElementById('noteContent');

//const selectorButtons = [chooseStickyNoteButton, chooseNotepadButton, choosePlaceholderButton]
const selectorButtons = [chooseStickyNoteButton, chooseNotepadButton]

selectorButtons.forEach(button => {
    button.addEventListener("click", () => {
        noteTitleInput.classList.remove(`size-${currentNoteType}`)
        noteContentInput.classList.remove(`size-${currentNoteType}`);

        currentNoteType = button.dataset.type;

        noteTitleInput.classList.add(`size-${currentNoteType}`);
        noteContentInput.classList.add(`size-${currentNoteType}`);

        updateActiveButton();
    });
});

function updateActiveButton() {
    noteTitleInput.classList.add(`size-${currentNoteType}`);
    noteContentInput.classList.add(`size-${currentNoteType}`);

    selectorButtons.forEach(button => {
        if (button.dataset.type === currentNoteType) {
            button.classList.add('active');
        } else {
            button.classList.remove('active');
        }
    });
}

updateActiveButton();