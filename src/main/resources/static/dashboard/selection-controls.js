
let currentNoteType = 'StickyNote';

const chooseStickyNoteButton = document.getElementById('chooseStickyNote');
const chooseNotepadButton = document.getElementById('chooseNotepad');
const choosePlaceholderButton = document.getElementById('choosePlaceholder');

const selectorButtons = [chooseStickyNoteButton, chooseNotepadButton, choosePlaceholderButton]

selectorButtons.forEach(button => {
    button.addEventListener("click", () => {
        currentNoteType = button.dataset.type;
        updateActiveButton();
    });
});

function updateActiveButton() {
    selectorButtons.forEach(button => {
        if (button.dataset.type === currentNoteType) {
            button.classList.add('active');
        } else {
            button.classList.remove('active');
        }
    });
}

updateActiveButton();

const toggleNoteTypeButton = document.getElementById("typeToggleButton");

toggleNoteTypeButton.addEventListener('click', () => {
    // Flip the state
    currentNoteType = (currentNoteType === 'StickyNote') ? 'BigSheet' : 'StickyNote';

    // Update the button’s visual state
    if (currentNoteType === 'BigSheet') {
        toggleNoteTypeButton.classList.add('active');   // sheet‑size
        toggleNoteTypeButton.textContent = 'Sticky‑size note';
        noteContent.classList.add('sheet-size'); // resize textarea
    } else {
        toggleNoteTypeButton.classList.remove('active');
        toggleNoteTypeButton.textContent = 'Sheet‑size note';
        noteContent.classList.remove('sheet-size');
    }
});