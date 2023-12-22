// S2U-17
// When the "Show statistics" link is clicked a modal should open, load data
// and render add the data to the table

(() => {

    const statModalId = "stat-modal";
    const spinnerId = "stat-modal-spinner";
    const questionPoolIdAttr = "data-qp-id";
    const questionPoolTitleAttr = "data-qp-title";
    const showStatsButtonAttr = "data-show-statistics";
    // TODO Master merge: replace hideClassName
    // const hideClassName = "d-none";
    const hideClassName = "hidden";
    const flexClassName = "d-flex";

    // TODO Master merge: Remove B3 version
    // B3 version, needs JQuery, isolating it here
    async function openModal(modalElement) {
        const jqModalEl = $(modalElement);
        // Init modal
        jqModalEl.modal();

        // Init popovers
        jqModalEl.find("[data-toggle='tooltip']").tooltip()
    }

    // TODO Master merge: Uncomment and test modal and tooltip
    // B5 version
    // async function openModal(modalElement) {
    //     // Init modal
    //     new bootstrap.Modal(modalElement).show();

    //     // Init popovers
    //     modalElement.querySelectorAll("[data-toggle='tooltip']")
    //             .forEach((el) => new bootstrap.Tooltip(el));
    // }

    async function setLoading(modal, loading) {
        const table = modal.querySelector("table");
        const spinner = document.getElementById(spinnerId);

        if (loading) {
            spinner.classList.remove(hideClassName);
            spinner.classList.add(flexClassName);
            table.classList.add(hideClassName);
        } else {
            table.classList.remove(hideClassName);
            spinner.classList.remove(flexClassName);
            spinner.classList.add(hideClassName);
        }
    }

    async function updateTable(modal, siteId, questionPoolId, questionPoolTitle) {
        const table = modal.querySelector("table");
        const titleElement = modal.querySelector(`[${questionPoolTitleAttr}]`);
        titleElement.innerHTML = questionPoolTitle;

        setLoading(modal, true);

        siteId = encodeURIComponent(siteId);

        const response = await fetch(`/samigo-app/servlet/QuestionPoolStatistics?siteId=${siteId}&qpId=${questionPoolId}`);

        if (response.ok) {
            const tableData = await response.json();

            // Wait for all rows to be updated
            await Promise.all(Object.keys(tableData).map((key) => updateRow(table, key, tableData[key])))
        } else {
            console.error("Could not get statistics", response.statusText)
        }

        setLoading(modal, false);
    }

    async function updateRow(table, key, value) {
        const headerCell = table.querySelector(`[data-cell-${key}]`);
        const valueCell = headerCell?.parentElement.querySelector("td");

        if (valueCell) {
            valueCell.innerText = value;
        }
    }

    function openStatisticsModal(event) {
        event.preventDefault();

        const modal = document.getElementById(statModalId);
        const questionPoolId = event.target.getAttribute(questionPoolIdAttr);
        const questionPoolTitle = event.target.getAttribute(questionPoolTitleAttr);
        const siteId = portal?.siteId;

        if (siteId) {
            updateTable(modal, siteId, questionPoolId, questionPoolTitle);
            openModal(modal);
        } else {
            console.error("Could not get siteId for statistics modal");
        }
    }


    window.addEventListener("load", () => {
        document.querySelectorAll(`[${showStatsButtonAttr}][${questionPoolIdAttr}]`)
                .forEach(cancelButton => cancelButton.addEventListener("click", openStatisticsModal));
    });

})();
