// Select the input field and all calculator buttons
const inputField = document.querySelector('.input');
const buttons = document.querySelectorAll('.button');

// Variable to store the current calculation string
let currentCalculation = '';

// Add a click event listener to every button on the grid
buttons.forEach(button => {
    button.addEventListener('click', () => {
        const buttonText = button.innerText;

        if (buttonText === 'C') {
            // Clear everything
            currentCalculation = '';
            inputField.value = '';
        } 
        else if (buttonText === '=') {
            // Calculate the result safely
            try {
                // If input is empty, do nothing
                if (currentCalculation === '') return;

                // Evaluate the string expression
                // Function evaluates string safe from local window scope exploits
                let result = Function(`"use strict"; return (${currentCalculation})`)();
                
                // Handle division by zero or invalid calculations resulting in Infinity/NaN
                if (!isFinite(result)) {
                    inputField.value = "Error";
                    currentCalculation = '';
                } else {
                    // Update input with the answer and prepare for the next operation
                    inputField.value = result;
                    currentCalculation = result.toString();
                }
            } catch (error) {
                // Display error message if the math expression is invalid (e.g., "7++2")
                inputField.value = 'Error';
                currentCalculation = '';
            }
        } 
        else {
            // Prevent users from typing multiple math operators consecutively (e.g., "++" or "*/")
            const lastChar = currentCalculation.slice(-1);
            const operators = ['+', '-', '*', '/', '%', '.'];
            
            if (operators.includes(buttonText) && operators.includes(lastChar)) {
                // Replace the last operator with the newly pressed one instead of stacking them
                currentCalculation = currentCalculation.slice(0, -1) + buttonText;
            } else {
                // Append the number or valid operator to the sequence
                currentCalculation += buttonText;
            }
            
            // Display the ongoing layout to the user
            inputField.value = currentCalculation;
        }
    });
});
