
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